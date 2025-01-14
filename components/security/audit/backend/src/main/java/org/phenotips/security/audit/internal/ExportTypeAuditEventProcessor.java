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
package org.phenotips.security.audit.internal;

import org.phenotips.security.audit.AuditEvent;
import org.phenotips.security.audit.spi.AuditEventProcessor;

import org.xwiki.component.annotation.Component;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.xpn.xwiki.XWikiContext;

/**
 * If the action is {@code export}, adds the export format as extra information.
 *
 * @version $Id$
 * @since 1.4
 */
@Component
@Named("export-type")
@Singleton
public class ExportTypeAuditEventProcessor implements AuditEventProcessor
{
    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Override
    public AuditEvent process(AuditEvent event)
    {
        if ("export".equals(event.getAction()) && event.getExtraInformation() == null) {
            String mode = this.xcontextProvider.get().getRequest().getParameter("format");
            return new AuditEvent(event.getUser(), event.getIp(), event.getAction(), mode, event.getEntity(),
                event.getTime());
        }
        return event;
    }
}
