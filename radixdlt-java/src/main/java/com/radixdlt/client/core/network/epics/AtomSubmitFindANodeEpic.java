package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.ledger.selector.RadixPeerSelector;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Observable;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomSubmitFindANodeEpic implements RadixNetworkEpic {
	private static final Logger LOGGER = LoggerFactory.getLogger(AtomSubmitFindANodeEpic.class);
	private final FindANodeMiniEpic findANode;

	public AtomSubmitFindANodeEpic(RadixPeerSelector selector) {
		this.findANode = new FindANodeMiniEpic(selector);
	}

	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		return updates
			.filter(u -> u instanceof AtomSubmissionUpdate)
			.map(AtomSubmissionUpdate.class::cast)
			.filter(update -> update.getState().equals(AtomSubmissionState.SEARCHING_FOR_NODE))
			.flatMap(searchUpdate ->
				findANode.apply(searchUpdate.getAtom().getRequiredFirstShard(), networkState)
					.map(a ->
						a.getType().equals(NodeUpdateType.SELECT_NODE)
							? AtomSubmissionUpdate.submit(searchUpdate.getUuid(), searchUpdate.getAtom(), a.getNode())
							: a
					)
			);
	}
}