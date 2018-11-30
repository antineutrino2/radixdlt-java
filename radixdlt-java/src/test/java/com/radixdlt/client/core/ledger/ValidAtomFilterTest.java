package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.AtomObservation;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.Test;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.Serialization;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.observers.TestObserver;

public class ValidAtomFilterTest {

	@Test
	public void testDownWithMissingUp() {
		RadixAddress accountReference = mock(RadixAddress.class);
		ECPublicKey ecPublicKey = mock(ECPublicKey.class);
		when(accountReference.getPublicKey()).thenReturn(ecPublicKey);

		RadixAddress address = mock(RadixAddress.class);
		when(address.ownsKey(any(ECKeyPair.class))).thenReturn(true);
		when(address.ownsKey(any(ECPublicKey.class))).thenReturn(true);

		Particle down = mock(Particle.class);
		when(down.getAddresses()).thenReturn(Collections.singleton(address));

		Serialization dson = mock(Serialization.class);
		when(dson.toDson(down, Output.HASH)).thenReturn(new byte[] {0});

		Particle up = mock(Particle.class);
		when(up.getAddresses()).thenReturn(Collections.singleton(address));
		when(dson.toDson(up, Output.HASH)).thenReturn(new byte[] {1});

		// Build atom with consumer originating from nowhere
		Atom atom = mock(Atom.class);
		when(atom.particles(Spin.DOWN)).then(inv -> Stream.of(down));
		when(atom.particles(Spin.UP)).then(inv -> Stream.of(up));
		AtomObservation atomObservation = mock(AtomObservation.class);
		when(atomObservation.getAtom()).thenReturn(atom);

		ValidAtomFilter validAtomFilter = new ValidAtomFilter(address, dson);

		TestObserver<AtomObservation> observer = TestObserver.create();
		validAtomFilter.filter(atomObservation).subscribe(observer);
		observer.assertValueCount(0);
	}

	@Test
	public void testDownBeforeUp() {
		RadixAddress accountReference = mock(RadixAddress.class);
		ECPublicKey ecPublicKey = mock(ECPublicKey.class);
		when(accountReference.getPublicKey()).thenReturn(ecPublicKey);

		RadixAddress address = mock(RadixAddress.class);
		when(address.ownsKey(ecPublicKey)).thenReturn(true);

		Particle down0 = mock(Particle.class);
		when(down0.getAddresses()).thenReturn(Collections.singleton(address));

		Serialization dson = mock(Serialization.class);
		when(dson.toDson(down0, Output.HASH)).thenReturn(new byte[] {0});

		Particle up1 = mock(Particle.class);
		when(up1.getAddresses()).thenReturn(Collections.singleton(address));
		when(dson.toDson(up1, Output.HASH)).thenReturn(new byte[] {1});

		Atom atom = mock(Atom.class);
		when(atom.particles(Spin.DOWN)).then(inv -> Stream.of(down0));
		when(atom.particles(Spin.UP)).then(inv -> Stream.of(up1));
		AtomObservation atomObservation = mock(AtomObservation.class);
		when(atomObservation.getAtom()).thenReturn(atom);

		Particle up0 = mock(Particle.class);
		when(up0.getAddresses()).thenReturn(Collections.singleton(address));
		when(dson.toDson(up0, Output.HASH)).thenReturn(new byte[] {0});

		Particle down2 = mock(Particle.class);
		when(down2.getAddresses()).thenReturn(Collections.singleton(mock(RadixAddress.class)));
		when(dson.toDson(down2, Output.HASH)).thenReturn(new byte[] {2});

		Atom oldAtom = mock(Atom.class);
		when(oldAtom.particles(Spin.DOWN)).then(inv -> Stream.of(down2));
		when(oldAtom.particles(Spin.UP)).then(inv -> Stream.of(up0));
		AtomObservation oldAtomObservation = mock(AtomObservation.class);
		when(oldAtomObservation.getAtom()).thenReturn(oldAtom);

		TestObserver<AtomObservation> observer = TestObserver.create();

		/* Make sure we don't count it unless we find the matching consumable */
		ValidAtomFilter validAtomFilter = new ValidAtomFilter(address, dson);
		validAtomFilter.filter(atomObservation);
		validAtomFilter.filter(oldAtomObservation).subscribe(observer);
		observer.assertValues(oldAtomObservation, atomObservation);
	}
}