package com.radixdlt.client.application.translate.unique;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

public class UniqueIdAction implements Action {

	/**
	 * Address for uniqueness constraint
	 */
	private final RadixAddress address;

	/**
	 * Unique identifier
	 */
	private final String unique;

	public UniqueIdAction(RadixAddress address, String unique) {
		Objects.requireNonNull(address);
		Objects.requireNonNull(unique);

		this.address = address;
		this.unique = unique;
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getUnique() {
		return unique;
	}
}