package com.radixdlt.client.core.atoms;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A group of particles representing one intent, e.g. a transfer.
 * <p>
 * * @author flotothemoon
 */
@SerializerId2("PARTICLEGROUP")
public class ParticleGroup extends SerializableObject {
	@JsonProperty("particles")
	@DsonOutput(DsonOutput.Output.ALL)
	private final List<SpunParticle> particles = new ArrayList<>();

	private ParticleGroup() {
	}

	public ParticleGroup(List<SpunParticle> particles) {
		Objects.requireNonNull(particles, "particles is required");

		if (particles.size() < 1) {
			throw new IllegalArgumentException("ParticleGroup cannot be empty, zero particles given");
		}

		this.particles.addAll(particles);
	}

	/**
	 * Get a stream of the spun particles in this group
	 */
	public final Stream<SpunParticle> spunParticles() {
		return this.particles.stream();
	}

	/**
	 * Get a stream of particles of a certain spin in this group
	 *
	 * @param spin The spin to filter by
	 * @return The particles in this group with that spin
	 */
	public final Stream<Particle> particles(Spin spin) {
		return this.spunParticles().filter(p -> p.getSpin() == spin).map(SpunParticle::getParticle);
	}

	/**
	 * Get a {@link ParticleGroup} with just one spun particle with the given spin and particle
	 *
	 * @param particle The particle
	 * @param spin     The spin
	 * @return The resulting particle group
	 */
	public static ParticleGroup just(Particle particle, Spin spin) {
		return of(SpunParticle.of(particle, spin));
	}

	/**
	 * Get a {@link ParticleGroup} consisting of the given particles
	 */
	public static ParticleGroup of(Iterable<SpunParticle> particles) {
		Objects.requireNonNull(particles, "particles is required");

		return new ParticleGroup(Lists.newArrayList(particles));
	}

	/**
	 * Get a {@link ParticleGroup} consisting of the given particles
	 */
	public static ParticleGroup of(SpunParticle<?>... particles) {
		Objects.requireNonNull(particles, "particles is required");

		return new ParticleGroup(Arrays.asList(particles));
	}
}
