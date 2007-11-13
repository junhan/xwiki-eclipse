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
 *
 */
package org.xwiki.xeclipse.model;

import java.util.Collection;

/**
 * This interface provides full access to space information.
 */
public interface IXWikiSpace
{
    public String getKey();

    public String getName();

    public String getType();

    public String getUrl();

    public String getDescription();

    public String getHomePage();

    /**
     * @return The connection where this space has been fetched from.
     */
    public IXWikiConnection getConnection();

    public Collection<IXWikiPage> getPages() throws XWikiConnectionException;

    public IXWikiPage createPage(String name, String content) throws XWikiConnectionException;

    public void remove() throws XWikiConnectionException;
}
