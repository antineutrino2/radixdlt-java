package com.radixdlt.client.application.translate.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.identity.Data;
import com.radixdlt.client.application.translate.data.DecryptedMessage.EncryptionState;
import com.radixdlt.client.application.translate.AtomToExecutedActionsMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.quarks.DataQuark;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps an atom to some number of sent message actions and decrypted.
 */
public class AtomToDecryptedMessageMapper implements AtomToExecutedActionsMapper<DecryptedMessage> {
	private static final JsonParser JSON_PARSER = new JsonParser();
	private final RadixUniverse universe;

	public AtomToDecryptedMessageMapper(RadixUniverse universe) {
		this.universe = universe;
	}

	public Observable<DecryptedMessage> map(Atom atom, RadixIdentity identity) {
		final Optional<MessageParticle> bytesParticle = atom.getDataParticles().stream()
			.filter(p -> !"encryptor".equals(p.getMetaData("application")))
			.findFirst();

		if (!bytesParticle.isPresent()) {
			return Observable.empty();
		}

		// TODO: don't pass in maps, utilize a metadata builder?
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", atom.getTimestamp());
		metaData.put("signatures", atom.getSignatures());

		bytesParticle.ifPresent(p -> metaData.compute("application", (k, v) -> p.getMetaData("application")));

		final Optional<MessageParticle> encryptorParticle = atom.getDataParticles().stream()
			.filter(p -> "encryptor".equals(p.getMetaData("application")))
			.findAny();
		metaData.put("encrypted", encryptorParticle.isPresent());

		final Encryptor encryptor;
		if (encryptorParticle.isPresent()) {
			JsonArray protectorsJson = JSON_PARSER.parse(
				new String(encryptorParticle.get().getQuarkOrError(DataQuark.class).getBytes(),
					StandardCharsets.UTF_8)).getAsJsonArray();
			List<EncryptedPrivateKey> protectors = new ArrayList<>();
			protectorsJson.forEach(protectorJson -> protectors.add(EncryptedPrivateKey.fromBase64(protectorJson.getAsString())));
			encryptor = new Encryptor(protectors);
		} else {
			encryptor = null;
		}

		RadixAddress from = bytesParticle.get().getFrom();
		RadixAddress to = bytesParticle.get().getAddresses().stream()
			.filter(addr -> !addr.equals(from))
			.findAny()
			.orElse(from);

		final byte[] bytes = bytesParticle.get().getQuarkOrError(DataQuark.class).getBytes();
		final Data data = Data.raw(bytes, metaData, encryptor);

		return identity.decrypt(data)
			.map(u -> {
				final EncryptionState encryptionState = encryptorParticle.isPresent()
					? EncryptionState.DECRYPTED : EncryptionState.NOT_ENCRYPTED;
				return new DecryptedMessage(u.getData(), from, to, encryptionState, atom.getTimestamp());
			})
			.onErrorResumeNext(e -> {
				if (e instanceof CryptoException) {
					return Single.just(
						new DecryptedMessage(bytes, from, to, EncryptionState.CANNOT_DECRYPT, atom.getTimestamp())
					);
				} else {
					return Single.error(e);
				}
			})
			.toObservable();
	}

}
