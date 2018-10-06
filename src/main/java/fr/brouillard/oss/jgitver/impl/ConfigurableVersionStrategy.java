/**
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver] (matthieu@brouillard.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.brouillard.oss.jgitver.impl;

import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import fr.brouillard.oss.jgitver.Version;
import fr.brouillard.oss.jgitver.VersionCalculationException;
import fr.brouillard.oss.jgitver.metadata.MetadataRegistrar;
import fr.brouillard.oss.jgitver.metadata.Metadatas;

public class ConfigurableVersionStrategy extends MaxVersionStrategy<ConfigurableVersionStrategy> {
    private boolean autoIncrementPatch = false;
    private boolean useDistance = true;
    private boolean useCommitTimestamp = false;
    private boolean useGitCommitId = false;
    private int gitCommitIdLength = 8;
    private boolean useDirty = false;
    private boolean useLongFormat;

    public ConfigurableVersionStrategy(VersionNamingConfiguration vnc, Repository repository, Git git, MetadataRegistrar metadatas) {
        super(vnc, repository, git, metadatas);
    }

    @Override
    public Version build(Commit head, List<Commit> parents) throws VersionCalculationException {
        try {
            Commit base = findVersionCommit(head, parents);
            Ref tagToUse = findTagToUse(head, base);
            Version baseVersion = getBaseVersionAndRegisterMetadata(base,tagToUse);

            final boolean useSnapshot = baseVersion.isSnapshot();

            if (!isBaseCommitOnHead(head, base) && autoIncrementPatch && !useLongFormat) {
                // we are not on head
                if (GitUtils.isAnnotated(tagToUse)) {
                    // found tag to use was an annotated one, lets' increment the version automatically
                    baseVersion = baseVersion.incrementPatch();
                }
            }

            if ((useDistance || useLongFormat) && !useSnapshot) {
                if (tagToUse == null) {
                    // no tag was found, let's count from initial commit
                    baseVersion = baseVersion.addQualifier("" + base.getHeadDistance());
                } else {
                    // use distance when long format is asked
                    // or not on head
                    // or if on head with a light tag
                    if (useLongFormat || !isBaseCommitOnHead(head, base) || !GitUtils.isAnnotated(tagToUse)) {
                        baseVersion = baseVersion.addQualifier("" + base.getHeadDistance());
                    }
                }
            }

            getRegistrar().registerMetadata(Metadatas.COMMIT_DISTANCE, "" + base.getHeadDistance());

            boolean needsCommitTimestamp = useCommitTimestamp && !useSnapshot;

            try (RevWalk walk = new RevWalk(getRepository())) {
                RevCommit rc = walk.parseCommit(head.getGitObject());
                String commitTimestamp = GitUtils.getTimestamp(rc.getAuthorIdent().getWhen().toInstant());
                getRegistrar().registerMetadata(Metadatas.COMMIT_TIMESTAMP, commitTimestamp);
                if (needsCommitTimestamp) {
                    baseVersion = baseVersion.addQualifier(commitTimestamp);
                }
            }

            boolean needsCommitId = useGitCommitId
                    && !(isBaseCommitOnHead(head, base)
                    && !baseVersion.noQualifier().equals(Version.DEFAULT_VERSION));

            if (useLongFormat || needsCommitId) {
                String commitIdQualifier =
                        (useLongFormat ? "g" : "") + head.getGitObject().getName().substring(0, useLongFormat ? 8 : gitCommitIdLength);
                baseVersion = baseVersion.addQualifier(commitIdQualifier);
            }

            if (!GitUtils.isDetachedHead(getRepository())) {
                String branch = getRepository().getBranch();
                baseVersion = enhanceVersionWithBranch(baseVersion, branch);
            } else {
                // ugly syntax to bypass the final/effectively final pb to access vraiable in lambda
                Optional<String> externalyProvidedBranchName = GitUtils.providedBranchName();
                if (externalyProvidedBranchName.isPresent()) {
                    baseVersion = enhanceVersionWithBranch(baseVersion, externalyProvidedBranchName.get());
                }
            }

            if (useDirty && isGitDirty()) {
                baseVersion = baseVersion.addQualifier("dirty");
            }

            return useSnapshot ? baseVersion.removeQualifier("SNAPSHOT").addQualifier("SNAPSHOT") : baseVersion;
        } catch (Exception ex) {
            throw new VersionCalculationException("cannot compute version", ex);
        }
    }

    public ConfigurableVersionStrategy setAutoIncrementPatch(boolean autoIncrementPatch) {
        return runAndGetSelf(() -> this.autoIncrementPatch = autoIncrementPatch);
    }

    public ConfigurableVersionStrategy setUseDistance(boolean useDistance) {
        return runAndGetSelf(() -> this.useDistance = useDistance);
    }

    public ConfigurableVersionStrategy setUseCommitTimestamp(boolean useCommitTimestamp) {
        return runAndGetSelf(() -> this.useCommitTimestamp = useCommitTimestamp);
    }

    public ConfigurableVersionStrategy setUseGitCommitId(boolean useGitCommitId) {
        return runAndGetSelf(() -> this.useGitCommitId = useGitCommitId);
    }

    public ConfigurableVersionStrategy setGitCommitIdLength(int gitCommitIdLength) {
        return runAndGetSelf(() -> this.gitCommitIdLength = gitCommitIdLength);
    }

    public ConfigurableVersionStrategy setUseDirty(boolean useDirty) {
        return runAndGetSelf(() -> this.useDirty = useDirty);
    }

    public ConfigurableVersionStrategy setUseLongFormat(boolean useLongFormat) {
        return runAndGetSelf(() -> this.useLongFormat = useLongFormat);
    }

}