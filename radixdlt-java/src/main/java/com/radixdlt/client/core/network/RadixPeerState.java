package com.radixdlt.client.core.network;

import com.radixdlt.client.core.address.RadixUniverseConfig;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable state at a certain point in time of a {@link RadixPeer}
 */
public class RadixPeerState {
	private final String location;
	private final int port;
	private final RadixClientStatus status;
	private final NodeRunnerData data;
	private final Integer version;
	private final RadixUniverseConfig universeConfig;

	public RadixPeerState(String location, int port, RadixClientStatus status, NodeRunnerData data, Integer version,
	                      RadixUniverseConfig universeConfig) {
		Objects.requireNonNull(location, "location is required");
		Objects.requireNonNull(status, "status is required");

		this.location = location;
		this.port = port;
		this.status = status;
		this.data = data;
		this.version = version;
		this.universeConfig = universeConfig;
	}

	@Override
	public String toString() {
		return "RadixPeerState{"
				+ "location='" + location + '\''
				+ ", port=" + port
				+ ", status=" + status
				+ ", data=" + data
				+ ", version=" + version
				+ ", universeConfig=" + universeConfig
				+ '}';
	}

	/**
	 * Location of {@link RadixPeer}
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * Port of {@link RadixPeer}
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Status of {@link RadixPeer}'s client
	 */
	public RadixClientStatus getStatus() {
		return status;
	}

	/**
	 * Node runner data of {@link RadixPeer}, may be null
	 */
	public Optional<NodeRunnerData> getData() {
		return Optional.ofNullable(this.data);
	}

	/**
	 * API Version of {@link RadixPeer}'s client, may be null
	 */
	public Optional<Integer> getVersion() {
		return Optional.ofNullable(this.version);
	}

	/**
	 * Universe configuration of {@link RadixPeer}'s client, may be null
	 */
	public Optional<RadixUniverseConfig> getUniverseConfig() {
		return Optional.ofNullable(this.universeConfig);
	}
}