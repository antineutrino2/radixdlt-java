package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.atommodel.tokens.FeeParticle;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.ledger.TransitionedParticle;

/**
 * Reduces particles at an address to it's token balances
 */
public class TokenBalanceReducer implements ParticleReducer<TokenBalanceState> {
	@Override
	public TokenBalanceState initialState() {
		return new TokenBalanceState();
	}

	@Override
	public TokenBalanceState reduce(TokenBalanceState state, TransitionedParticle t) {
		Particle p = t.getParticle();
		if (!(p instanceof OwnedTokensParticle) || p instanceof FeeParticle) {
			return state;
		}

		return TokenBalanceState.merge(state, (TransitionedParticle<OwnedTokensParticle>) t);
	}
}
