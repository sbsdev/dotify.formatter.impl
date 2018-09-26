package org.daisy.dotify.formatter.impl.search;

import java.util.HashSet;
import java.util.Map;

import com.github.krukow.clj_ds.Persistents;
import com.github.krukow.clj_lang.IPersistentMap;

public class LookupHandler<K, V> implements Cloneable {
	private IPersistentMap<K, V> keyValueMap;
	private IPersistentMap<K, V> uncommitted;
	// private HashSet<K> requestedKeys;
	private Runnable setDirty;
	
	LookupHandler(Runnable setDirty) {
		this.keyValueMap = (IPersistentMap)Persistents.hashMap();
		this.uncommitted = (IPersistentMap)Persistents.hashMap();
		// this.requestedKeys = new HashSet<>();
		this.setDirty = setDirty;
	}

	V get(K key) {
		return get(key, null);
	}

	V get(K key, V def) {
		// requestedKeys.add(key);
		V ret = keyValueMap.valAt(key);
		if (ret==null) {
			setDirty.run();
			//ret is null here, so if def is also null, either variable can be returned
			return def;
		} else {
			return ret;
		}
	}

	/**
	 * Keeps a value for later commit. Unlike put, multiple calls to keep for the same key will not
	 * affect the status of the LookupHandler. Upon calling commit, the final value for each key are put
	 * and, if changed, affects the status of the LookupHandler. Note that kept variables cannot
	 * be read unless committed.
	 *
	 * @param key the value to keep
	 * @param value the value
	 */
	void keep(K key, V value) {
		uncommitted = uncommitted.assoc(key, value);
	}
	
	/**
	 * Commits values stored with keep.
	 */
	void commit() {
		if (uncommitted.count() > 0) {
			for (Map.Entry<K,V> e : uncommitted) {
				put(e.getKey(), e.getValue());
			}
			uncommitted = (IPersistentMap)Persistents.hashMap();
		}
	}
	
	// FIXME: move to Builder and/or return new LookupHandler?
	// -> in this case clone method can also be removed
	void put(K key, V value) {
		V prv = keyValueMap.valAt(key);
		keyValueMap = keyValueMap.assoc(key, value);
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
