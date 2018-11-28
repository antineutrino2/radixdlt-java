package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import java.math.BigDecimal;

public class BurnTokensAction implements Action {
	private final TokenClassReference tokenClassReference;
	private final long amount;

	public BurnTokensAction(TokenClassReference tokenClassReference, long amount) {
		this.tokenClassReference = tokenClassReference;
		this.amount = amount;
	}

	public TokenClassReference getTokenClassReference() {
		return tokenClassReference;
	}

	public BigDecimal getAmount() {
		return new BigDecimal(amount);
	}
}