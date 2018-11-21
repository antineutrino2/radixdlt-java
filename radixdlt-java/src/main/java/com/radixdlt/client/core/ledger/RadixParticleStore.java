package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;

public class RadixParticleStore implements ParticleStore {
	private final AtomStore atomStore;
	private final ConcurrentHashMap<RadixAddress, Observable<TransitionedParticle>> cache = new ConcurrentHashMap<>();

	public RadixParticleStore(AtomStore atomStore) {
		this.atomStore = atomStore;
	}

	public Observable<TransitionedParticle> getParticles(RadixAddress address) {
		// TODO: use https://github.com/JakeWharton/RxReplayingShare to disconnect when unsubscribed
		return cache.computeIfAbsent(address, addr -> atomStore.getAtoms(address)
			.filter(AtomObservation::hasAtom)
			.flatMap(observation -> Observable.<TransitionedParticle>create(emitter -> {
				try {
					observation.getAtom().spunParticles()
						.filter(s -> s.getParticle().getAddresses().contains(address))
						.forEach(s -> emitter.onNext(TransitionedParticle.fromSpunParticle(s, observation.getType())));
				} catch (Throwable e) {
					emitter.onError(e);
				}
			}))
			.cache()
		);
	}
}
