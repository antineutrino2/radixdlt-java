package com.radixdlt.client.core.network;

import com.google.gson.JsonArray;
import com.radixdlt.client.core.atoms.AtomEvent;
import java.util.List;
import java.util.UUID;

import org.json.JSONObject;
import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.JsonJavaType;
import org.radix.serialization2.Serialization;
import org.radix.serialization2.client.GsonJson;
import org.radix.serialization2.client.Serialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;

/**
 * Responsible for managing the state across one web socket connection to a Radix Node.
 * This consists of mainly keeping track of JSON-RPC method calls and JSON-RPC subscription
 * calls.
 */
public class RadixJsonRpcClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixJsonRpcClient.class);

	/**
	 * Betanet does not yet support version checking
	 * TODO: this is temporary, remove once supported everywhere
	 */
	private static final boolean CHECK_API_VERSION = false;

	/**
	 * API version of Client, must match with Server
	 */
	private static final Integer API_VERSION = 1;

	/**
	 * The websocket this is wrapping
	 */
	private final WebSocketClient wsClient;

	/**
	 * Hot observable of messages received through the websocket
	 */
	private final Observable<JsonObject> messages;

	/**
	 * Cached API version of Node
	 */
	private final Single<Integer> serverApiVersion;

	/**
	 * Cached Universe of Node
	 */
	private final Single<RadixUniverseConfig> universeConfig;

	public RadixJsonRpcClient(WebSocketClient wsClient) {
		this.wsClient = wsClient;

		final JsonParser parser = new JsonParser();
		this.messages = this.wsClient.getMessages()
			.map(msg -> parser.parse(msg).getAsJsonObject())
			.publish()
			.refCount();

		if (!CHECK_API_VERSION) {
			this.serverApiVersion = Single.just(API_VERSION);
		} else {
			this.serverApiVersion = jsonRpcCall("Api.getVersion")
				.map(result -> result.getAsJsonObject().get("version").getAsInt())
				.cache();
		}

		Serialization serialization = Serialize.getInstance();
		this.universeConfig = jsonRpcCall("Universe.getUniverse")
			.map(element -> GsonJson.getInstance().stringFromGson(element))
			.map(result -> serialization.fromJson(result, RadixUniverseConfig.class))
			.cache();
	}

	/**
	 * @return URL which websocket is connected to
	 */
	public String getLocation() {
		return wsClient.getEndpoint().url().toString();
	}

	public Observable<RadixClientStatus> getStatus() {
		return wsClient.getStatus();
	}

	/**
	 * Attempts to close the websocket this client is connected to.
	 * If there are still observers connected to the websocket closing
	 * will not occur.
	 *
	 * @return true if websocket was successfully closed, false otherwise
	 */
	public boolean tryClose() {
		return this.wsClient.close();
	}

	/**
	 * Generic helper method for calling a JSON-RPC method. Deserializes the received json.
	 *
	 * @param method name of JSON-RPC method
	 * @return response from rpc method
	 */
	private Single<JsonElement> jsonRpcCall(String method, JsonObject params) {
		return this.wsClient.connect().andThen(
			Single.<JsonElement>create(emitter -> {
				final String uuid = UUID.randomUUID().toString();

				final JsonObject requestObject = new JsonObject();
				requestObject.addProperty("id", uuid);
				requestObject.addProperty("method", method);
				requestObject.add("params", params);

				messages
					.filter(msg -> msg.has("id"))
					.filter(msg -> msg.get("id").getAsString().equals(uuid))
					.firstOrError()
					.doOnSubscribe(disposable -> {
						boolean sendSuccess = wsClient.send(GsonJson.getInstance().stringFromGson(requestObject));
						if (!sendSuccess) {
							disposable.dispose();
							emitter.onError(new RuntimeException("Could not connect."));
						}
					})
					.subscribe(
						msg -> {
							final JsonObject received = msg.getAsJsonObject();
							if (received.has("result")) {
								emitter.onSuccess(received.get("result"));
							} else if (received.has("error")) {
								emitter.onError(new JsonRpcException(requestObject, received));
							} else {
								emitter.onError(
									new RuntimeException("Received bad json rpc message: " + received.toString())
								);
							}
						},
						err -> {
							emitter.onError(new RuntimeException(err.getMessage()));
						}
					);
			})
		);
	}

	/**
	 * Generic helper method for calling a JSON-RPC method with no parameters. Deserializes the received json.
	 *
	 * @param method name of JSON-RPC method
	 * @return response from rpc method
	 */
	private Single<JsonElement> jsonRpcCall(String method) {
		return this.jsonRpcCall(method, new JsonObject());
	}

	public Single<Integer> getAPIVersion() {
		return serverApiVersion;
	}

	public Single<Boolean> checkAPIVersion() {
		return this.getAPIVersion().map(API_VERSION::equals);
	}

	/**
	 * Retrieve the universe the node is supporting. The result is cached for future calls.
	 *
	 * @return universe config which the node is supporting
	 */
	public Single<RadixUniverseConfig> getUniverse() {
		return this.universeConfig;
	}

	/**
	 * Retrieve the node data for node we are connected to
	 *
	 * @return node data for node we are connected to
	 */
	public Single<NodeRunnerData> getInfo() {
		return this.jsonRpcCall("Network.getInfo")
				.map(result -> Serialize.getInstance().fromJson(result.toString(), RadixSystem.class))
				.map(UDPNodeRunnerData::new);
	}

	/**
	 * Retrieve list of nodes this node knows about
	 *
	 * @return list of nodes this node knows about
	 */
	public Single<List<NodeRunnerData>> getLivePeers() {
		JsonJavaType listOfNodeRunnerData = Serialize.getInstance().jsonCollectionType(List.class, NodeRunnerData.class);
		return this.jsonRpcCall("Network.getLivePeers")
				.map(result -> Serialize.getInstance().fromJson(result.toString(), listOfNodeRunnerData));
	}


	/**
	 * Connects to this Radix Node if not already connected and queries for an atom by HID.
	 * If the node does not carry the atom (e.g. if it does not reside on the same shard) then
	 * this method will return an empty Maybe.
	 *
	 * @param hid the hash id of the atom being queried
	 * @return the atom if found, if not, return an empty Maybe
	 */
	public Maybe<Atom> getAtom(EUID hid) {
		JsonObject params = new JsonObject();
		params.addProperty("hid", hid.toString());

		JsonJavaType listOfAtom = Serialize.getInstance().jsonCollectionType(List.class, Atom.class);
		return this.jsonRpcCall("Ledger.getAtoms", params)
			.<List<Atom>>map(result -> Serialize.getInstance().fromJson(result.toString(), listOfAtom))
			.flatMapMaybe(list -> list.isEmpty() ? Maybe.empty() : Maybe.just(list.get(0)));
	}

	/**
	 * Generic helper method for creating a subscription via JSON-RPC.
	 *
	 * @param method name of subscription method
	 * @param notificationMethod name of the JSON-RPC notification method
	 * @return Observable of emitted subscription json elements
	 */
	public Observable<JsonElement> jsonRpcSubscribe(String method, String notificationMethod) {
		return this.jsonRpcSubscribe(method, new JsonObject(), notificationMethod);
	}

	/**
	 * Generic helper method for creating a subscription via JSON-RPC.
	 *
	 * @param method name of subscription method
	 * @param rawParams parameters to subscription method
	 * @param notificationMethod name of the JSON-RPC notification method
	 * @return Observable of emitted subscription json elements
	 */
	public Observable<JsonElement> jsonRpcSubscribe(String method, JsonObject rawParams, String notificationMethod) {
		return this.wsClient.connect().andThen(
			Observable.create(emitter -> {
				final String subscriberId = UUID.randomUUID().toString();
				final JsonObject params = rawParams.deepCopy();
				params.addProperty("subscriberId", subscriberId);

				Disposable subscriptionDisposable = messages
					.filter(msg -> msg.has("method"))
					.filter(msg -> msg.get("method").getAsString().equals(notificationMethod))
					.map(msg -> msg.get("params").getAsJsonObject())
					.filter(p -> p.get("subscriberId").getAsString().equals(subscriberId))
					.subscribe(
						emitter::onNext,
						emitter::onError
					);

				Disposable methodDisposable = this.jsonRpcCall(method, params)
					.subscribe(
						msg -> { },
						emitter::onError
					);

				emitter.setCancellable(() -> {
					methodDisposable.dispose();
					subscriptionDisposable.dispose();

					final String cancelUuid = UUID.randomUUID().toString();
					JsonObject cancelObject = new JsonObject();
					cancelObject.addProperty("id", cancelUuid);
					cancelObject.addProperty("method", "Atoms.cancel");
					JsonObject cancelParams = new JsonObject();
					cancelParams.addProperty("subscriberId", subscriberId);
					cancelObject.add("params", cancelParams);
					wsClient.send(GsonJson.getInstance().stringFromGson(cancelObject));
				});
			})
		);
	}

	/**
	 *  Retrieves all atoms from a node specified by a query. This includes all past
	 *  and future atoms. The Observable returned will never complete.
	 *
	 * @param atomQuery query specifying which atoms to retrieve
	 * @return observable of atoms
	 */
	public Observable<AtomObservation> getAtoms(AtomQuery atomQuery) {
		final JsonObject params = new JsonObject();
		params.add("query", atomQuery.toJson());

		return this.jsonRpcSubscribe("Atoms.subscribe", params, "Atoms.subscribeUpdate")
			.map(JsonElement::getAsJsonObject)
			.flatMap(observedAtomsJson -> {
				JsonArray atomEvents = observedAtomsJson.getAsJsonArray("atomEvents");
				boolean isHead = observedAtomsJson.has("isHead") && observedAtomsJson.get("isHead").getAsBoolean();

				return Observable.fromIterable(atomEvents)
					.map(jsonAtom -> Serialize.getInstance().fromJson(jsonAtom.toString(), AtomEvent.class))
					.map(AtomObservation::ofEvent)
					.concatWith(Maybe.fromCallable(() -> isHead ? AtomObservation.head() : null));
			});
	}

	/**
	 * Attempt to submit an atom to a node. Returns the status of the atom as it
	 * gets stored on the node.
	 *
	 * @param atom the atom to submit
	 * @return observable of the atom as it gets stored
	 */
	public Observable<AtomSubmissionUpdate> submitAtom(Atom atom) {
		return Observable.<AtomSubmissionUpdate>create(emitter -> {
			JSONObject jsonAtomTemp = Serialize.getInstance().toJsonObject(atom, Output.API);
			JsonElement jsonAtom = GsonJson.getInstance().toGson(jsonAtomTemp);

			final String subscriberId = UUID.randomUUID().toString();
			JsonObject params = new JsonObject();
			params.addProperty("subscriberId", subscriberId);
			params.add("atom", jsonAtom);

			Disposable subscriptionDisposable = messages
				.filter(msg -> msg.has("method"))
				.filter(msg -> msg.get("method").getAsString().equals("AtomSubmissionState.onNext"))
				.map(msg -> msg.get("params").getAsJsonObject())
				.filter(p -> p.get("subscriberId").getAsString().equals(subscriberId))
				.map(p -> {
					final AtomSubmissionState state = AtomSubmissionState.valueOf(p.get("value").getAsString());
					final JsonElement data;
					if (p.has("data")) {
						data = p.get("data");
					} else {
						data = null;
					}

					if (state == AtomSubmissionState.VALIDATION_ERROR) {
						LOGGER.warn(jsonAtom.toString());
					}

					AtomSubmissionUpdate update = AtomSubmissionUpdate.create(atom, state, data);
					update.putMetaData("jsonRpcParams", params);
					return update;
				})
				.takeUntil(AtomSubmissionUpdate::isComplete)
				.subscribe(
					emitter::onNext,
					emitter::onError,
					emitter::onComplete
				);


			Disposable methodDisposable = this.jsonRpcCall("Universe.submitAtomAndSubscribe", params)
				.doOnSubscribe(
					disposable -> emitter.onNext(
						AtomSubmissionUpdate.create(atom, AtomSubmissionState.SUBMITTING)
					)
				)
				.subscribe(
					msg -> emitter.onNext(AtomSubmissionUpdate.create(atom, AtomSubmissionState.SUBMITTED)),
					throwable -> {
						if (throwable instanceof JsonRpcException) {
							JsonRpcException e = (JsonRpcException) throwable;
							LOGGER.warn(e.getRequest().toString());
							LOGGER.warn(e.getError().toString());
						}

						emitter.onNext(
							AtomSubmissionUpdate.create(
								atom,
								AtomSubmissionState.FAILED,
								new JsonPrimitive(throwable.getMessage())
							)
						);
						emitter.onComplete();
					}
				);

			emitter.setCancellable(() -> {
				methodDisposable.dispose();
				subscriptionDisposable.dispose();
			});
		});
	}

	@Override
	public String toString() {
		return wsClient.toString();
	}
}
