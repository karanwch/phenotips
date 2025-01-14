/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package org.phenotips.vocabularies.rest;

import org.phenotips.rest.ParentResource;
import org.phenotips.rest.Relation;

import org.xwiki.stability.Unstable;

import javax.annotation.Nonnull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Resource for for fetching multiple vocabulary terms from a specified vocabulary, given a list of identifiers.
 *
 * @version $Id$
 * @since 1.4
 */
@Unstable("New API introduced in 1.4")
@Path("/vocabularies/{vocabulary-id}/fetch")
@ParentResource(VocabularyResource.class)
@Relation("https://phenotips.org/rel/vocabularyTerm")
public interface VocabularyTermsResource
{
    /**
     * Retrieves a JSON representation of the {@link org.phenotips.vocabulary.VocabularyTerm} objects by searching the
     * specified vocabulary. The following request parameters are used:
     *
     * <dl>
     * <dt>term-id</dt>
     * <dd>a list of term IDs that should be retrieved</dd>
     * </dl>
     *
     * @param vocabularyId the vocabulary identifier, which is also used as a prefix in every term identifier from that
     *            vocabulary, for example {@code HP} or {@code MIM}
     * @return the requested terms, excepting those that do not exist in the specified vocabulary
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Response getTerms(@Nonnull @PathParam("vocabulary-id") String vocabularyId);
}
