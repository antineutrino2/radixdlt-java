package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.ledger.selector.RadixPeerSelector;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate.AtomsFetchState;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Observable;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomsFetchFindANodeEpic implements RadixNetworkEpic {
	private static final Logger LOGGER = LoggerFactory.getLogger(AtomSubmitFindANodeEpic.class);
	private final FindANodeMiniEpic findANode;

	public AtomsFetchFindANodeEpic(RadixPeerSelector selector) {
		this.findANode = new FindANodeMiniEpic(selector);
	}

	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		return updates
			.filter(u -> u instanceof AtomsFetchUpdate)
			.map(AtomsFetchUpdate.class::cast)
			.filter(update -> update.getState().equals(AtomsFetchState.SEARCHING_FOR_NODE))
			.flatMap(searchUpdate ->
				findANode.apply(Collections.singleton(searchUpdate.getAddress().getUID().getShard()), networkState)
					.map(a ->
						a.getType().equals(NodeUpdateType.SELECT_NODE)
							? AtomsFetchUpdate.submitQuery(searchUpdate.getUuid(), searchUpdate.getAddress(), a.getNode())
							: a
					)
			);
	}

}