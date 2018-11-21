package com.radixdlt.client.application.translate.tokens;

import java.math.BigDecimal;

import org.junit.Test;
import org.radix.utils.UInt256;

import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.RadixHash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.radixdlt.client.core.ledger.TransitionedParticle;

public class TokenBalanceReducerTest {

	@Test
	public void testSimpleBalance() {
		OwnedTokensParticle ownedTokensParticle = mock(OwnedTokensParticle.class);
		RadixHash hash = mock(RadixHash.class);
		when(ownedTokensParticle.getAmount()).thenReturn(UInt256.TEN);
		when(ownedTokensParticle.getHash()).thenReturn(hash);
		when(ownedTokensParticle.getDson()).thenReturn(new byte[] {1});
		TokenClassReference token = mock(TokenClassReference.class);
		when(ownedTokensParticle.getTokenClassReference()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance = reducer.reduce(new TokenBalanceState(), TransitionedParticle.n2u(ownedTokensParticle));
		BigDecimal tenSubunits = TokenClassReference.subunitsToUnits(UInt256.TEN);
		assertThat(tokenBalance.getBalance().get(token).getAmount().compareTo(tenSubunits)).isEqualTo(0);
	}

	@Test
	public void transitionAndRevertTest() {
		OwnedTokensParticle ownedTokensParticle = mock(OwnedTokensParticle.class);
		RadixHash hash = mock(RadixHash.class);
		when(ownedTokensParticle.getAmount()).thenReturn(UInt256.TEN);
		when(ownedTokensParticle.getHash()).thenReturn(hash);
		when(ownedTokensParticle.getDson()).thenReturn(new byte[] {1});
		TokenClassReference token = mock(TokenClassReference.class);
		when(ownedTokensParticle.getTokenClassReference()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance0 = reducer.reduce(new TokenBalanceState(), TransitionedParticle.n2u(ownedTokensParticle));
		TokenBalanceState tokenBalance1 = reducer.reduce(tokenBalance0, TransitionedParticle.u2n(ownedTokensParticle));


		assertThat(tokenBalance1.getBalance().get(token).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	public void twoTransitionsAndRevertTest() {
		OwnedTokensParticle ownedTokensParticle = mock(OwnedTokensParticle.class);
		RadixHash hash = mock(RadixHash.class);
		when(ownedTokensParticle.getAmount()).thenReturn(UInt256.TEN);
		when(ownedTokensParticle.getHash()).thenReturn(hash);
		when(ownedTokensParticle.getDson()).thenReturn(new byte[] {1});
		TokenClassReference token = mock(TokenClassReference.class);
		when(ownedTokensParticle.getTokenClassReference()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance0 = reducer.reduce(new TokenBalanceState(), TransitionedParticle.n2u(ownedTokensParticle));
		TokenBalanceState tokenBalance1 = reducer.reduce(tokenBalance0, TransitionedParticle.u2d(ownedTokensParticle));
		TokenBalanceState tokenBalance2 = reducer.reduce(tokenBalance1, TransitionedParticle.d2u(ownedTokensParticle));

		BigDecimal tenSubunits = TokenClassReference.subunitsToUnits(UInt256.TEN);
		assertThat(tokenBalance2.getBalance().get(token).getAmount()).isEqualByComparingTo(tenSubunits);
	}
}