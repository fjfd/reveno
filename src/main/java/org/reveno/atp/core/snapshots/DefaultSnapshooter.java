/** 
 *  Copyright (c) 2015 The original author or authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.reveno.atp.core.snapshots;

import org.reveno.atp.api.RepositorySnapshooter;
import org.reveno.atp.api.domain.RepositoryData;
import org.reveno.atp.core.api.channel.Buffer;
import org.reveno.atp.core.api.channel.Channel;
import org.reveno.atp.core.api.serialization.RepositoryDataSerializer;
import org.reveno.atp.core.api.storage.SnapshotStorage;
import org.reveno.atp.core.api.storage.SnapshotStorage.SnapshotStore;
import org.reveno.atp.core.channel.NettyBasedBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSnapshooter implements RepositorySnapshooter {

	public static final long TYPE = 0x12345;

	@Override
	public long getType() {
		return TYPE;
	}
	
	@Override
	public boolean hasAny() {
		return storage.getLastSnapshotStore() != null;
	}

	@Override
	public void snapshoot(RepositoryData repo) {
		SnapshotStore snap = storage.nextSnapshotStore();
		
		Buffer buffer = new NettyBasedBuffer(false);
		repoSerializer.serialize(repo, buffer);
		try (Channel c = storage.channel(snap.getSnapshotPath())) {
			log.info("Performing repository snapshot to " + snap);
			
			c.write(buffer, false);
		} catch (Throwable t) {
			log.error("", t);
			throw new RuntimeException(t);
		}
	}

	@Override
	public RepositoryData load() {
		if (storage.getLastSnapshotStore() == null)
			return null;
		
		SnapshotStore snap = storage.getLastSnapshotStore();
		Buffer buffer = new NettyBasedBuffer(false);
		try (Channel c = storage.channel(snap.getSnapshotPath())) {
			log.info("Loading repository snapshot to " + snap);
			while (c.isReadAvailable())
				c.read(buffer);
			
			return repoSerializer.deserialize(buffer);
		} catch (Throwable t) {
			log.error("", t);
			throw new RuntimeException(t);
		}
	}

	public DefaultSnapshooter(
			SnapshotStorage storage,
			RepositoryDataSerializer repoSerializer) {
		this.storage = storage;
		this.repoSerializer = repoSerializer;
	}

	
	protected final SnapshotStorage storage;
	protected final RepositoryDataSerializer repoSerializer;
	protected static final Logger log = LoggerFactory.getLogger(DefaultSnapshooter.class);

}