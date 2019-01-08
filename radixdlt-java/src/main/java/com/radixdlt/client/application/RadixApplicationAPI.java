package com.radixdlt.client.application;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radixdlt.client.application.identity.Data;
import com.radixdlt.client.application.identity.Data.DataBuilder;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionExecutionException.ActionExecutionExceptionBuilder;
import com.radixdlt.client.application.translate.ActionStore;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.ApplicationStore;
import com.radixdlt.client.application.translate.AtomErrorToExceptionReasonMapper;
import com.radixdlt.client.application.translate.AtomToExecutedActionsMapper;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.application.translate.StatefulActionToParticlesMapper;
import com.radixdlt.client.application.translate.StatelessActionToParticlesMapper;
import com.radixdlt.client.application.translate.atomic.AtomicToParticlesMapper;
import com.radixdlt.client.application.translate.data.AtomToDecryptedMessageMapper;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.application.translate.data.SendMessageToParticlesMapper;
import com.radixdlt.client.application.translate.tokenclasses.BurnTokensAction;
import com.radixdlt.client.application.translate.tokenclasses.BurnTokensActionMapper;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenToParticlesMapper;
import com.radixdlt.client.application.translate.tokenclasses.MintTokensAction;
import com.radixdlt.client.application.translate.tokenclasses.MintTokensActionMapper;
import com.radixdlt.client.application.translate.tokenclasses.TokenClassesReducer;
import com.radixdlt.client.application.translate.tokenclasses.TokenClassesState;
import com.radixdlt.client.application.translate.tokenclasses.TokenState;
import com.radixdlt.client.application.translate.tokens.AtomToTokenTransfersMapper;
import com.radixdlt.client.application.translate.tokens.TokenBalanceReducer;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState;
import com.radixdlt.client.application.translate.tokens.TokenClassReference;
import com.radixdlt.client.application.translate.tokens.TokenTransfer;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.application.translate.tokens.TransferTokensToParticlesMapper;
import com.radixdlt.client.application.translate.unique.AlreadyUsedUniqueIdReasonMapper;
import com.radixdlt.client.application.translate.unique.PutUniqueIdToParticlesMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.timestamp.TimestampParticle;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.RadixUniverse.Ledger;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.annotations.Nullable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.observables.ConnectableObservable;
import org.radix.utils.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Radix Dapp API, a high level api which dapps can utilize. The class hides
 * the complexity of Atoms and cryptography and exposes a simple high level interface.
 */
public class RadixApplicationAPI {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixApplicationAPI.class);

	public static class Result {
		private final ConnectableObservable<SubmitAtomAction> updates;
		private final Completable completable;

		private Result(ConnectableObservable<SubmitAtomAction> updates, Completable completable) {
			this.updates = updates;
			this.completable = completable;
		}

		private Result connect() {
			this.updates.connect();
			return this;
		}

		/**
		 * A low level interface, returns an a observable of the status of an atom submission as it occurs.
		 *
		 * @return observable of atom submission status
		 */
		public Observable<SubmitAtomAction> toObservable() {
			return this.updates;
		}

		/**
		 * A high level interface, returns completable of successful completion of action execution.
		 * If there is an with the ledger, the completable throws an ActionExecutionException.
		 *
		 * @return completable of successful execution of action onto ledger.
		 */
		public Completable toCompletable() {
			return this.completable;
		}
	}

	private final RadixIdentity identity;
	private final RadixUniverse universe;

	private final Map<Class<?>, ActionStore<?>> actionStores;

	private final Map<Class<? extends ApplicationState>, ApplicationStore<? extends ApplicationState>> applicationStores;

	/**
	 * Action to Particle Mappers which can mapToParticles without any dependency on ledger state
	 */
	private final List<StatelessActionToParticlesMapper> statelessActionToParticlesMappers;

	/**
	 * Action to Particle Mappers which require dependencies on the ledger
	 */
	private final List<StatefulActionToParticlesMapper> statefulActionToParticlesMappers;

	/**
	 * Mapper of atom submission errors to application level errors
	 */
	private final List<AtomErrorToExceptionReasonMapper> atomErrorMappers;

	// TODO: Translator from particles to atom
	private final FeeMapper feeMapper;

	private final Ledger ledger;

	private RadixApplicationAPI(
			RadixIdentity identity,
			RadixUniverse universe,
			FeeMapper feeMapper,
			Ledger ledger,
			List<StatelessActionToParticlesMapper> statelessActionToParticlesMappers,
			List<StatefulActionToParticlesMapper> statefulActionToParticlesMappers,
			List<ParticleReducer<? extends ApplicationState>> particleReducers,
			List<AtomToExecutedActionsMapper<? extends Object>> atomMappers,
			List<AtomErrorToExceptionReasonMapper> atomErrorMappers
	) {
		Objects.requireNonNull(identity);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(feeMapper);
		Objects.requireNonNull(ledger);
		Objects.requireNonNull(statelessActionToParticlesMappers);
		Objects.requireNonNull(statefulActionToParticlesMappers);
		Objects.requireNonNull(particleReducers);
		Objects.requireNonNull(atomErrorMappers);

		this.identity = identity;
		this.universe = universe;
		this.actionStores = atomMappers.stream().collect(Collectors.toMap(
				AtomToExecutedActionsMapper::actionClass,
				m -> new ActionStore<>(ledger.getAtomStore(), m)
		));
		this.applicationStores = particleReducers.stream().collect(Collectors.toMap(
				ParticleReducer::stateClass,
				r -> new ApplicationStore<>(ledger.getParticleStore(), r)
		));
		this.statefulActionToParticlesMappers = statefulActionToParticlesMappers;
		this.statelessActionToParticlesMappers = statelessActionToParticlesMappers;
		this.atomErrorMappers = atomErrorMappers;
		this.feeMapper = feeMapper;
		this.ledger = ledger;
	}

	public static class RadixApplicationAPIBuilder {
		private RadixIdentity identity;
		private RadixUniverse universe;
		private FeeMapper feeMapper;
		private List<ParticleReducer<? extends ApplicationState>> reducers = new ArrayList<>();
		private List<StatelessActionToParticlesMapper> statelessActionToParticlesMappers = new ArrayList<>();
		private List<StatefulActionToParticlesMapper> statefulActionToParticlesMappers = new ArrayList<>();
		private List<AtomToExecutedActionsMapper<? extends Object>> atomMappers = new ArrayList<>();
		private List<AtomErrorToExceptionReasonMapper> atomErrorMappers = new ArrayList<>();

		public RadixApplicationAPIBuilder() {
		}

		public RadixApplicationAPIBuilder addAtomMapper(AtomToExecutedActionsMapper<? extends Object> atomMapper) {
			this.atomMappers.add(atomMapper);
			return this;
		}

		public RadixApplicationAPIBuilder addStatelessParticlesMapper(StatelessActionToParticlesMapper mapper) {
			this.statelessActionToParticlesMappers.add(mapper);
			return this;
		}

		public RadixApplicationAPIBuilder addStatefulParticlesMapper(StatefulActionToParticlesMapper mapper) {
			this.statefulActionToParticlesMappers.add(mapper);
			return this;
		}

		public <T extends ApplicationState> RadixApplicationAPIBuilder addReducer(ParticleReducer<T> reducer) {
			this.reducers.add(reducer);
			return this;
		}

		public RadixApplicationAPIBuilder addAtomErrorMapper(AtomErrorToExceptionReasonMapper atomErrorMapper) {
			this.atomErrorMappers.add(atomErrorMapper);
			return this;
		}

		public RadixApplicationAPIBuilder feeMapper(FeeMapper feeMapper) {
			this.feeMapper = feeMapper;
			return this;
		}

		public RadixApplicationAPIBuilder defaultFeeMapper() {
			this.feeMapper = new PowFeeMapper(p -> new Atom(Collections.singletonList(ParticleGroup.of(p))).getHash(),
					new ProofOfWorkBuilder());
			return this;
		}

		public RadixApplicationAPIBuilder identity(RadixIdentity identity) {
			this.identity = identity;
			return this;
		}

		public RadixApplicationAPIBuilder universe(RadixUniverse universe) {
			this.universe = universe;
			return this;
		}

		public RadixApplicationAPI build() {
			Objects.requireNonNull(this.identity, "Identity must be specified");
			Objects.requireNonNull(this.feeMapper, "Fee Mapper must be specified");

			if (this.universe == null && !RadixUniverse.isInstantiated()) {
				throw new IllegalStateException("No universe available.");
			}

			final RadixUniverse universe = this.universe == null ? RadixUniverse.getInstance() : this.universe;
			final Ledger ledger = universe.getLedger();
			final FeeMapper feeMapper = this.feeMapper;
			final RadixIdentity identity = this.identity;
			final List<ParticleReducer<? extends ApplicationState>> reducers = this.reducers;

			return new RadixApplicationAPI(
					identity,
					universe,
					feeMapper,
					ledger,
					this.statelessActionToParticlesMappers,
					this.statefulActionToParticlesMappers,
					reducers,
					this.atomMappers,
					this.atomErrorMappers
			);
		}
	}

	/**
	 * Creates an API with the default actions and reducers
	 *
	 * @param identity the identity of user of API
	 * @return an api instance
	 */
	public static RadixApplicationAPI create(RadixIdentity identity) {
		Objects.requireNonNull(identity);

		return new RadixApplicationAPIBuilder()
				.identity(identity)
				.defaultFeeMapper()
				.addStatelessParticlesMapper(new SendMessageToParticlesMapper(ECKeyPairGenerator.newInstance()::generateKeyPair))
				.addStatelessParticlesMapper(new CreateTokenToParticlesMapper())
				.addStatelessParticlesMapper(new PutUniqueIdToParticlesMapper())
				.addStatelessParticlesMapper(new AtomicToParticlesMapper())
				.addStatefulParticlesMapper(new MintTokensActionMapper())
				.addStatefulParticlesMapper(new BurnTokensActionMapper(RadixUniverse.getInstance()))
				.addStatefulParticlesMapper(new TransferTokensToParticlesMapper(RadixUniverse.getInstance()))
				.addReducer(new TokenClassesReducer())
				.addReducer(new TokenBalanceReducer())
				.addAtomMapper(new AtomToDecryptedMessageMapper(RadixUniverse.getInstance()))
				.addAtomMapper(new AtomToTokenTransfersMapper(RadixUniverse.getInstance()))
				.addAtomErrorMapper(new AlreadyUsedUniqueIdReasonMapper())
				.build();
	}

	public Observable<RadixNetworkState> getNetworkState() {
		return this.universe.getNetworkState();
	}

	private ApplicationStore<? extends ApplicationState> getStore(Class<? extends ApplicationState> storeClass) {
		ApplicationStore<? extends ApplicationState> store = this.applicationStores.get(storeClass);
		if (store == null) {
			throw new IllegalArgumentException("No store available for class: " + storeClass);
		}
		return store;
	}

	private ActionStore<?> getActionStore(Class<?> actionClass) {
		ActionStore<?> store = this.actionStores.get(actionClass);
		if (store == null) {
			throw new IllegalArgumentException("No store available for class: " + actionClass);
		}
		return store;
	}

	/**
	 * Idempotent method which prefetches atoms in user's account
	 * TODO: what to do when no puller available
	 *
	 * @return Disposable to dispose to stop pulling
	 */
	public Disposable pull() {
		return this.pull(this.getMyAddress());
	}

	/**
	 * Idempotent method which prefetches atoms in an address
	 * TODO: what to do when no puller available
	 *
	 * @param address the address to pull atoms from
	 * @return Disposable to dispose to stop pulling
	 */
	public Disposable pull(RadixAddress address) {
		Objects.requireNonNull(address);

		if (this.ledger.getAtomPuller() != null) {
			return this.ledger.getAtomPuller().pull(address).subscribe();
		} else {
			return Disposables.disposed();
		}
	}

	/**
	 * Returns the native Token Reference found in the genesis atom
	 *
	 * @return the native token reference
	 */
	public TokenClassReference getNativeTokenRef() {
		return this.universe.getNativeToken();
	}

	/**
	 * Returns a hot observable with the latest token state of the native token
	 *
	 * @return a hot observable of latest state of the native token
	 */
	public Observable<TokenState> getNativeTokenClass() {
		return this.getTokenClass(this.getNativeTokenRef());
	}

	/**
	 * Returns a never ending hot observable of the actions performed at a given address.
	 * If the given address is not currently being pulled this will pull for atoms in that
	 * address automatically until the observable is disposed.
	 *
	 * @param actionClass the Action class
	 * @param address     the address to retrieve the state of
	 * @param <T>         the Action class
	 * @return a hot observable of the actions at the given address
	 */
	public <T> Observable<T> getActions(Class<T> actionClass, RadixAddress address) {
		final Observable<AtomObservation> atomsPulled = this.ledger.getAtomPuller() != null
				? this.ledger.getAtomPuller().pull(address)
				: Observable.never();
		Observable<AtomObservation> auto = atomsPulled.publish()
				.refCount(2);
		Disposable d = auto.subscribe();

		return this.getActionStore(actionClass).getActions(address, this.identity)
				.map(actionClass::cast)
				.publish()
				.autoConnect()
				.doOnSubscribe(disposable -> auto.subscribe().dispose())
				.doOnError(e -> d.dispose())
				.doOnDispose(d::dispose)
				.doOnComplete(d::dispose);
	}

	/**
	 * Returns a never ending hot observable of the state of a given address.
	 * If the given address is not currently being pulled this will pull for atoms in that
	 * address automatically until the observable is disposed.
	 *
	 * @param stateClass the ApplicationState class
	 * @param address    the address to retrieve the state of
	 * @param <T>        the ApplicationState class
	 * @return a hot observable of a state of the given address
	 */
	public <T extends ApplicationState> Observable<T> getState(Class<T> stateClass, RadixAddress address) {
		final Observable<AtomObservation> atomsPulled = this.ledger.getAtomPuller() != null
				? this.ledger.getAtomPuller().pull(address)
				: Observable.never();
		Observable<AtomObservation> auto = atomsPulled.publish()
				.refCount(2);
		Disposable d = auto.subscribe();

		return this.getStore(stateClass).getState(address)
				.map(stateClass::cast)
				.publish()
				.autoConnect()
				.doOnSubscribe(disposable -> auto.subscribe().dispose())
				.doOnError(e -> d.dispose())
				.doOnDispose(d::dispose)
				.doOnComplete(d::dispose);
	}

	/**
	 * Returns a hot observable of the latest state of token classes at a given
	 * address
	 *
	 * @param address the address of the account to check
	 * @return a hot observable of the latest state of token classes
	 */
	public Observable<TokenClassesState> getTokenClasses(RadixAddress address) {
		return this.getState(TokenClassesState.class, address);
	}

	/**
	 * Returns a hot observable of the latest state of token classes at the user's
	 * address
	 *
	 * @return a hot observable of the latest state of token classes
	 */
	public Observable<TokenClassesState> getMyTokenClasses() {
		return this.getTokenClasses(this.getMyAddress());
	}

	/**
	 * Returns a hot observable of the latest state of a given token
	 *
	 * @return a hot observable of the latest state of the token
	 */
	public Observable<TokenState> getTokenClass(TokenClassReference ref) {
		return this.getTokenClasses(ref.getAddress())
				.flatMapMaybe(m -> Optional.ofNullable(m.getState().get(ref)).map(Maybe::just).orElse(Maybe.empty()));
	}

	public ECPublicKey getMyPublicKey() {
		return this.identity.getPublicKey();
	}

	public RadixIdentity getMyIdentity() {
		return this.identity;
	}

	public RadixAddress getMyAddress() {
		return this.universe.getAddressFrom(this.identity.getPublicKey());
	}

	public Observable<DecryptedMessage> getMessages() {
		return this.getMessages(this.getMyAddress());
	}

	public Observable<DecryptedMessage> getMessages(RadixAddress address) {
		Objects.requireNonNull(address);
		return this.getActions(DecryptedMessage.class, address);
	}

	public Result sendMessage(byte[] data, boolean encrypt) {
		return this.sendMessage(data, encrypt, this.getMyAddress());
	}

	public Result sendMessage(byte[] data, boolean encrypt, RadixAddress address) {
		SendMessageAction sendMessageAction = new SendMessageAction(data, this.getMyAddress(), address, encrypt);

		return this.execute(sendMessageAction);
	}

	public Observable<TokenTransfer> getMyTokenTransfers() {
		return this.getTokenTransfers(this.getMyAddress());
	}

	public Observable<TokenTransfer> getTokenTransfers(RadixAddress address) {
		Objects.requireNonNull(address);
		return this.getActions(TokenTransfer.class, address);
	}

	public Observable<Map<TokenClassReference, BigDecimal>> getBalance(RadixAddress address) {
		Objects.requireNonNull(address);
		return this.getState(TokenBalanceState.class, address)
				.map(TokenBalanceState::getBalance)
				.map(map -> map.entrySet().stream().collect(
						Collectors.toMap(Entry::getKey, e -> e.getValue().getAmount())
				));
	}

	public Observable<BigDecimal> getMyBalance(TokenClassReference tokenClassReference) {
		return this.getBalance(this.getMyAddress(), tokenClassReference);
	}

	public Observable<BigDecimal> getBalance(RadixAddress address, TokenClassReference token) {
		Objects.requireNonNull(token);

		return this.getBalance(address)
				.map(balances -> Optional.ofNullable(balances.get(token)).orElse(BigDecimal.ZERO));
	}

	/**
	 * Creates a third party token into the user's account
	 *
	 * @param name            The name of the token to create
	 * @param iso             The symbol of the token to create
	 * @param description     A description of the token
	 * @param initialSupply   The initial amount in subunits of supply for this token
	 * @param granularity     The least multiple of subunits per transaction for this token
	 * @param tokenSupplyType The type of supply for this token: Fixed or Mutable
	 * @return result of the transaction
	 */
	public Result createToken(
			String name,
			String iso,
			String description,
			UInt256 initialSupply,
			UInt256 granularity,
			TokenSupplyType tokenSupplyType
	) {
		CreateTokenAction tokenCreation = new CreateTokenAction(
				this.getMyAddress(), name, iso, description, initialSupply, granularity, tokenSupplyType);
		return this.execute(tokenCreation);
	}

	/**
	 * Mints an amount of new tokens into the user's account
	 *
	 * @param iso    The symbol of the token to mint
	 * @param amount The amount in subunits to mint
	 * @return result of the transaction
	 */
	public Result mintTokens(String iso, UInt256 amount) {
		MintTokensAction mintTokensAction = new MintTokensAction(TokenClassReference.of(this.getMyAddress(), iso), amount);
		return this.execute(mintTokensAction);
	}


	/**
	 * Burns an amount of tokens in the user's account
	 *
	 * @param iso    The symbol of the token to mint
	 * @param amount The amount to mint
	 * @return result of the transaction
	 */
	public Result burnTokens(String iso, UInt256 amount) {
		BurnTokensAction burnTokensAction = new BurnTokensAction(TokenClassReference.of(this.getMyAddress(), iso), amount);
		return this.execute(burnTokensAction);
	}

	/**
	 * Sends an amount of a token to an address
	 *
	 * @param to     the address to send tokens to
	 * @param amount the amount and token type
	 * @return result of the transaction
	 */
	public Result sendTokens(RadixAddress to, BigDecimal amount, TokenClassReference token) {
		return this.transferTokens(this.getMyAddress(), to, amount, token);
	}

	/**
	 * Sends an amount of a token with a message attachment to an address
	 *
	 * @param to      the address to send tokens to
	 * @param amount  the amount and token type
	 * @param message message to be encrypted and attached to transfer
	 * @return result of the transaction
	 */
	public Result sendTokens(
			RadixAddress to,
			BigDecimal amount,
			TokenClassReference token,
			@Nullable String message
	) {
		final Data attachment;
		if (message != null) {
			attachment = new DataBuilder()
					.addReader(to.getPublicKey())
					.addReader(this.getMyPublicKey())
					.bytes(message.getBytes()).build();
		} else {
			attachment = null;
		}

		return this.transferTokens(this.getMyAddress(), to, amount, token, attachment);
	}

	/**
	 * Sends an amount of a token with a data attachment to an address
	 *
	 * @param to         the address to send tokens to
	 * @param amount     the amount and token type
	 * @param attachment the data attached to the transaction
	 * @return result of the transaction
	 */
	public Result sendTokens(RadixAddress to, BigDecimal amount, TokenClassReference token, @Nullable Data attachment) {
		return this.transferTokens(this.getMyAddress(), to, amount, token, attachment);
	}


	public Result transferTokens(RadixAddress from, RadixAddress to, BigDecimal amount, TokenClassReference token) {
		return this.transferTokens(from, to, amount, token, null);
	}

	/**
	 * Sends an amount of a token with a data attachment to an address with a unique property
	 * meaning that no other transaction can be executed with the same unique bytes
	 *
	 * @param to         the address to send tokens to
	 * @param amount     the amount and token type
	 * @param attachment the data attached to the transaction
	 * @return result of the transaction
	 */
	public Result transferTokens(
			RadixAddress from,
			RadixAddress to,
			BigDecimal amount,
			TokenClassReference token,
			@Nullable Data attachment
	) {
		Objects.requireNonNull(from);
		Objects.requireNonNull(to);
		Objects.requireNonNull(amount);
		Objects.requireNonNull(token);

		final TransferTokensAction transferTokensAction =
				TransferTokensAction.create(from, to, amount, token, attachment);

		return this.execute(transferTokensAction);
	}

	private Observable<SpunParticle> statefulMappersToParticles(Observable<Action> actions) {
		return actions.flatMap(action ->
				Observable
						.fromIterable(this.statefulActionToParticlesMappers)
						.flatMap(mapper -> {
							final Observable<Observable<? extends ApplicationState>> context =
									mapper.requiredState(action).map(ctx -> this.getState(ctx.stateClass(), ctx.address()));
							return mapper.mapToParticles(action, context);
						})
		);
	}

	private Observable<Action> statefulMappersToSideEffects(Action action) {
		return Observable
				.fromIterable(this.statefulActionToParticlesMappers)
				.flatMap(mapper -> {
					final Observable<Observable<? extends ApplicationState>> context =
							mapper.requiredState(action).map(ctx -> this.getState(ctx.stateClass(), ctx.address()));
					return mapper.sideEffects(action, context);
				});
	}

	private Observable<Action> collectActionAndEffects(Action action) {
		Observable<Action> statelessEffects = Observable
				.fromIterable(this.statelessActionToParticlesMappers)
				.flatMap(mapper -> mapper.sideEffects(action));
		Observable<Action> statefulEffects = this.statefulMappersToSideEffects(action);

		return Observable.concat(statelessEffects, statefulEffects)
				.flatMap(RadixApplicationAPI.this::collectActionAndEffects)
				.startWith(action);
	}

	/**
	 * Returns a cold single of an unsigned atom given a user action. Note that this is
	 * method will always return a unique atom even if given equivalent actions
	 *
	 * @param action action to build a single atom
	 * @return a cold single of an atom mapped from an action
	 */
	public Single<UnsignedAtom> buildAtom(Action action) {
		final Observable<Action> allActions = this.collectActionAndEffects(action);
		final Observable<SpunParticle> statelessParticles = allActions.flatMap(a ->
				Observable
						.fromIterable(this.statelessActionToParticlesMappers)
						.flatMap(mapper -> mapper.mapToParticles(a))
		);
		final Observable<SpunParticle> statefulParticles = this.statefulMappersToParticles(allActions);
		final Observable<SpunParticle> timestampParticle =
				Observable.just(SpunParticle.up(new TimestampParticle(System.currentTimeMillis())));

		return Observable.concat(statelessParticles, statefulParticles, timestampParticle)
				.<List<SpunParticle>>scanWith(
						ArrayList::new,
						(a, b) -> Stream.concat(a.stream(), Stream.of(b)).collect(Collectors.toList())
				)
				.lastOrError()
				.map(particles -> {
					List<SpunParticle> allParticles = new ArrayList<>(particles);
					allParticles.addAll(this.feeMapper.map(particles, this.universe, this.getMyPublicKey()));
					return allParticles;
				})
				.map(particles -> new UnsignedAtom(new Atom(Collections.singletonList(ParticleGroup.of(particles)))));
	}

	private Result buildDisconnectedResult(Action action) {
		ConnectableObservable<SubmitAtomAction> updates = this.buildAtom(action)
				.flatMap(this.identity::sign)
				.flatMapObservable(this.ledger.getAtomSubmitter()::submitAtom)
				.replay();

		Completable completable = updates
				.filter(SubmitAtomResultAction.class::isInstance)
				.map(SubmitAtomResultAction.class::cast)
				.firstOrError()
				.flatMapCompletable(update -> {
					if (update.getType() == SubmitAtomResultActionType.STORED) {
						return Completable.complete();
					} else {
						JsonElement data = update.getData();
						final JsonObject errorData = data == null ? null : data.getAsJsonObject();
						final ActionExecutionExceptionBuilder exceptionBuilder = new ActionExecutionExceptionBuilder()
								.errorData(errorData);
						this.atomErrorMappers.stream()
								.flatMap(errorMapper -> errorMapper.mapAtomErrorToExceptionReasons(update.getAtom(), errorData))
								.forEach(exceptionBuilder::addReason);
						return Completable.error(exceptionBuilder.build());
					}
				});

		return new Result(updates, completable);
	}

	/**
	 * Immediately executes a user action onto the ledger. Note that this method is NOT
	 * idempotent.
	 *
	 * @param action action to execute
	 * @return results of the execution
	 */
	public Result execute(Action action) {
		return this.buildDisconnectedResult(action).connect();
	}

	/**
	 * Executes actions sequentially. If an action fails, then the completable this method
	 * returns will call onError immediately. Note that this method is NEITHER idempotent
	 * NOR atomic (i.e. if an action fails, all previous actions to that would still have occurred).
	 *
	 * @param actions the action to execute sequentially
	 * @return completion status of all of the actions
	 */
	public Completable executeSequentially(Action... actions) {
		Completable completable = Observable.fromIterable(Arrays.asList(actions))
				.concatMapCompletable(a -> this.buildDisconnectedResult(a).connect().toCompletable()).cache();
		completable.subscribe();
		return completable;
	}
}
