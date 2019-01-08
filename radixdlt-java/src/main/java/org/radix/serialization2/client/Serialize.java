package org.radix.serialization2.client;

import com.radixdlt.client.application.translate.unique.UniqueId;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.quarks.AccountableQuark;
import com.radixdlt.client.atommodel.quarks.ChronoQuark;
import com.radixdlt.client.atommodel.quarks.DataQuark;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;
import com.radixdlt.client.atommodel.quarks.IdentifiableQuark;
import com.radixdlt.client.atommodel.quarks.OwnableQuark;
import com.radixdlt.client.atommodel.quarks.UniqueQuark;
import com.radixdlt.client.atommodel.timestamp.TimestampParticle;
import com.radixdlt.client.atommodel.tokens.FeeParticle;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Quark;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.network.jsonrpc.RadixSystem;
import com.radixdlt.client.core.network.jsonrpc.TCPNodeRunnerData;
import com.radixdlt.client.core.network.jsonrpc.UDPNodeRunnerData;
import org.radix.serialization2.Serialization;
import org.radix.serialization2.SerializationPolicy;
import org.radix.serialization2.SerializerIds;

import java.util.Arrays;
import java.util.Collection;

public final class Serialize {

	private static class Holder {
		static final Serialization INSTANCE = Serialization.create(createIds(getClasses()), createPolicy(getClasses()));

		private static SerializerIds createIds(Collection<Class<?>> classes) {
			return CollectionScanningSerializerIds.create(classes);
		}

		private static SerializationPolicy createPolicy(Collection<Class<?>> classes) {
			return CollectionScanningSerializationPolicy.create(classes);
		}

		private static Collection<Class<?>> getClasses() {
			return Arrays.asList(
					Atom.class,
					RadixAddress.class,
					ParticleGroup.class,
					Particle.class,
					SpunParticle.class,
					TimestampParticle.class,
					OwnedTokensParticle.class,
					FeeParticle.class,
					MessageParticle.class,
					TokenParticle.class,
					UniqueParticle.class,

					Quark.class,
					ChronoQuark.class,
					DataQuark.class,
					FungibleQuark.class,
					IdentifiableQuark.class,
					OwnableQuark.class,
					UniqueQuark.class,
					AccountableQuark.class,

					ECKeyPair.class,
					ECSignature.class,
					RadixSystem.class,
					RadixUniverseConfig.class,
					TCPNodeRunnerData.class,
					RadixResourceIdentifer.class,
					UniqueId.class,
					UDPNodeRunnerData.class
			);
		}
	}

	private Serialize() {
		throw new IllegalStateException("Can't construct");
	}

	public static Serialization getInstance() {
		return Holder.INSTANCE;
	}
}
