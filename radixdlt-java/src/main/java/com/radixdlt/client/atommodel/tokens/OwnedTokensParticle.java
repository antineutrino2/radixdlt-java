package com.radixdlt.client.atommodel.tokens;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.Serialize;
import org.radix.utils.UInt256;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.quarks.AccountableQuark;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;
import com.radixdlt.client.atommodel.quarks.OwnableQuark;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.HashSet;

/**
 *  A particle which represents an amount of fungible tokens owned by some key owner and stored in an account.
 */
@SerializerId2("OWNEDTOKENSPARTICLE")
public class OwnedTokensParticle extends Particle {
	@JsonProperty("token_reference")
	@DsonOutput(DsonOutput.Output.ALL)
	private TokenClassReference tokenClassReference;

	protected OwnedTokensParticle() {
	}

	public OwnedTokensParticle(UInt256 amount, FungibleQuark.FungibleType type, RadixAddress address, long nonce,
	                        TokenClassReference tokenRef, long planck) {
		super(new OwnableQuark(address.getPublicKey()), new AccountableQuark(address),
				new FungibleQuark(amount, planck, nonce, type));

		this.tokenClassReference = tokenRef;
	}

	public RadixAddress getAddress() {
		return getQuarkOrError(AccountableQuark.class).getAddresses().get(0);
	}

	@Override
	public Set<RadixAddress> getAddresses() {
		return new HashSet<>(getQuarkOrError(AccountableQuark.class).getAddresses());
	}

	public void addConsumerQuantities(UInt256 amount, ECKeyPair newOwner, Map<ECKeyPair, UInt256> consumerQuantities) {
		if (amount.compareTo(getAmount()) > 0) {
			throw new IllegalArgumentException(
				"Unable to create consumable with amount " + amount + " (available: " + getAmount() + ")"
			);
		}

		if (amount.equals(getAmount())) {
			consumerQuantities.merge(newOwner, amount, UInt256::add);
			return;
		}

		consumerQuantities.merge(newOwner, amount, UInt256::add);
		consumerQuantities.merge(getAddress().toECKeyPair(), getAmount().subtract(amount), UInt256::add);
	}

	public FungibleQuark.FungibleType getType() {
		return getQuarkOrError(FungibleQuark.class).getType();
	}

	public long getPlanck() {
		return getQuarkOrError(FungibleQuark.class).getPlanck();
	}

	public long getNonce() {
		return getQuarkOrError(FungibleQuark.class).getNonce();
	}

	public TokenClassReference getTokenClassReference() {
		return tokenClassReference;
	}

	public UInt256 getAmount() {
		return getQuarkOrError(FungibleQuark.class).getAmount();
	}

	public Set<ECPublicKey> getOwnersPublicKeys() {
		return Collections.singleton(getQuarkOrError(OwnableQuark.class).getOwner());
	}

	public ECPublicKey getOwner() {
		return getQuarkOrError(OwnableQuark.class).getOwner();
	}

	public RadixHash getHash() {
		return RadixHash.of(getDson());
	}

	public byte[] getDson() {
		return Serialize.getInstance().toDson(this, DsonOutput.Output.HASH);
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " owners(" + getQuarkOrError(OwnableQuark.class).getOwner() + ")"
				+ " amount(" + getQuarkOrError(FungibleQuark.class).getAmount() + ")";
	}
}
