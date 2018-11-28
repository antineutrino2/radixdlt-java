package com.radixdlt.client.application.translate.tokens;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import java.math.BigDecimal;
import org.junit.Test;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Collections;

public class TransferTokensToParticlesMapperTest {

	@Test
	public void createTransactionWithNoFunds() {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixAddress address = mock(RadixAddress.class);


		TokenClassReference token = mock(TokenClassReference.class);
		when(token.getSymbol()).thenReturn("TEST");

		TransferTokensAction transferTokensAction = mock(TransferTokensAction.class);
		when(transferTokensAction.getAmount()).thenReturn(new BigDecimal("1.0"));
		when(transferTokensAction.getFrom()).thenReturn(address);
		when(transferTokensAction.getTokenClassReference()).thenReturn(token);

		TokenBalanceState state = mock(TokenBalanceState.class);
		when(state.getBalance()).thenReturn(Collections.emptyMap());

		TransferTokensToParticlesMapper transferTranslator = new TransferTokensToParticlesMapper(universe, addr -> Observable.just(state));
		TestObserver<SpunParticle> testObserver = TestObserver.create();
		transferTranslator.map(transferTokensAction).subscribe(testObserver);
		testObserver.assertError(new InsufficientFundsException(token, BigDecimal.ZERO, new BigDecimal("1.0")));
	}

}