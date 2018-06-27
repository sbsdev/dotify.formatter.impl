package org.daisy.dotify.formatter.impl.search;

import java.util.HashSet;

import org.daisy.dotify.common.collection.ImmutableMap;

public class LookupHandler<K, V> implements Cloneable {
	private ImmutableMap<K, V> keyValueMap;
	// private HashSet<K> requestedKeys;
	private Runnable setDirty;
	
	LookupHandler(Runnable setDirty) {
		this.keyValueMap = ImmutableMap.empty();
		// this.requestedKeys = new HashSet<>();
		this.setDirty = setDirty;
	}

	V get(K key) {
		return get(key, null);
	}

	V get(K key, V def) {
		// requestedKeys.add(key);
		V ret = keyValueMap.get(key);
		if (ret==null) {
			setDirty.run();
			//ret is null here, so if def is also null, either variable can be returned
			return def;
		} else {
			return ret;
		}
	}
	
	// FIXME: move to Builder and/or return new LookupHandler?
	// -> in this case clone method can also be removed
	void put(K key, V value) {
		V prv = keyValueMap.get(key);
		keyValueMap = ImmutableMap.put(keyValueMap, key, value);
		if (/*requestedKeys.contains(key) &&*/ prv!=null && !prv.equals(value)) {
			setDirty.run();
		}
	}

	// FIXME: or do this implicitly when creating a new CrossReferenceHandler from another (not a clone) ?
	// FIXME: or could we just skip the requestedKeys check?
	// -> why don't we set dirty if the key hasn't been requested yet?
	//    -> because in that case we can be sure the right value is used, so we don't need another iteration
	//    -> for now skip anyway
	
	// /**
	//  * Forget the requested keys (but not the values)
	//  */
	// void forgetRequests() {
	// 	requestedKeys.clear();
	// }

	@SuppressWarnings("unchecked")
	@Override
	public LookupHandler<K, V> clone() {
		LookupHandler<K, V> clone;
		try {
			clone = (LookupHandler<K, V>)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new InternalError("coding error");
		}
		// clone.requestedKeys = (HashSet<K>)requestedKeys.clone();
		return clone;
	}
}
