/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.eclipse.model;

import org.xwiki.eclipse.storage.AbstractDataManager;

/**
 * 
 * @version $Id$
 */
public abstract class XWikiEclipseWikiSummary extends ModelObject
{

    /**
     * @param dataManager
     */
    public XWikiEclipseWikiSummary(AbstractDataManager dataManager)
    {
        super(dataManager);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.eclipse.model.ModelObject#getXWikiEclipseId()
     */
    @Override
    public String getXWikiEclipseId()
    {
        return String.format(
            "xwikieclipse://%s/rest/wikis/%s", getDataManager().getName(), getWikiId()); //$NON-NLS-1$;
    }

    public abstract String getWikiId();
    
    public abstract String getName();
    
    public abstract String getUrl();
}