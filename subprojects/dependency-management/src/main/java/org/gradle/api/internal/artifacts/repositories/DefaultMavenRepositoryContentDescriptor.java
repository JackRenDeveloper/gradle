/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.repositories;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor;
import org.gradle.internal.Actions;

class DefaultMavenRepositoryContentDescriptor extends DefaultRepositoryContentDescriptor implements MavenRepositoryContentDescriptor {
    boolean snapshots = true;
    boolean releases = true;

    @Override
    public void releasesOnly() {
        snapshots = false;
        releases = true;
    }

    @Override
    public void snapshotsOnly() {
        snapshots = true;
        releases = false;
    }

    @Override
    public Action<? super ArtifactResolutionDetails> toContentFilter() {
        Action<? super ArtifactResolutionDetails> filter = super.toContentFilter();
        if (!snapshots || !releases) {
            Action<? super ArtifactResolutionDetails> action = (Action<ArtifactResolutionDetails>) details -> {
                if (!details.isVersionListing()) {
                    String version = details.getComponentId().getVersion();
                    if (snapshots && !version.endsWith("-SNAPSHOT")) {
                        details.notFound();
                        return;
                    }
                    if (releases && version.endsWith("-SNAPSHOT")) {
                        details.notFound();
                        return;
                    }
                }
            };
            if (filter == null) {
                return action;
            }
            return Actions.composite(filter, action);
        }
        return filter;
    }
}
