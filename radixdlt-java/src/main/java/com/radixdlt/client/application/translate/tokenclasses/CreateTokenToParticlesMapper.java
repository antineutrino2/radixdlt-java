package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionToParticlesMapper;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;

import io.reactivex.Observable;
import java.util.EnumMap;

/**
 * Maps the CreateToken action into it's corresponding particles
 */
public class CreateTokenToParticlesMapper implements ActionToParticlesMapper {
	public Observable<SpunParticle> map(Action action) {
		if (!(action instanceof CreateTokenAction)) {
			return Observable.empty();
		}

		CreateTokenAction tokenCreation = (CreateTokenAction) action;

		final TokenPermission mintPermissions;
		final TokenPermission burnPermissions;

		if (tokenCreation.getTokenSupplyType().equals(TokenSupplyType.FIXED)) {
			mintPermissions = TokenPermission.SAME_ATOM_ONLY;
			burnPermissions = TokenPermission.NONE;
		} else if (tokenCreation.getTokenSupplyType().equals(TokenSupplyType.MUTABLE)) {
			mintPermissions = TokenPermission.TOKEN_OWNER_ONLY;
			burnPermissions = TokenPermission.TOKEN_OWNER_ONLY;
		} else {
			throw new IllegalStateException("Unknown supply type: " + tokenCreation.getTokenSupplyType());
		}

		TokenParticle token = new TokenParticle(
				tokenCreation.getAddress(),
				tokenCreation.getName(),
				tokenCreation.getIso(),
				tokenCreation.getDescription(),
				new EnumMap<FungibleType, TokenPermission>(FungibleType.class) {{
					this.put(FungibleType.MINTED, mintPermissions);
					this.put(FungibleType.BURNED, burnPermissions);
					this.put(FungibleType.TRANSFERRED, TokenPermission.ALL);
				}},
				null
		);
		OwnedTokensParticle minted = new OwnedTokensParticle(
				tokenCreation.getInitialSupply() * TokenClassReference.SUB_UNITS,
				FungibleQuark.FungibleType.MINTED,
				tokenCreation.getAddress(),
				System.currentTimeMillis(),
				token.getTokenClassReference(),
				System.currentTimeMillis() / 60000L + 60000
		);

		return Observable.just(SpunParticle.up(token), SpunParticle.up(minted));
	}
}