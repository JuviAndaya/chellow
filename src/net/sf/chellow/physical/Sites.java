/*
 
 Copyright 2005, 2008 Meniscus Systems Ltd
 
 This file is part of Chellow.

 Chellow is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 Chellow is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Chellow; if not, write to the Free Software
 Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

 */

package net.sf.chellow.physical;

import java.util.List;

import net.sf.chellow.monad.Hiber;
import net.sf.chellow.monad.HttpException;
import net.sf.chellow.monad.Invocation;
import net.sf.chellow.monad.MonadUtils;
import net.sf.chellow.monad.NotFoundException;
import net.sf.chellow.monad.UserException;
import net.sf.chellow.monad.types.MonadUri;
import net.sf.chellow.monad.types.UriPathElement;
import net.sf.chellow.ui.Chellow;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Sites extends EntityList {
	// public static final SiteNameDataSource DEFAULT_SOURCE = new
	// DefaultSiteName();
	public static final UriPathElement URI_ID;
	// public static final Map<Long, SiteNameDataSource> sources = new
	// HashMap<Long, SiteNameDataSource>();
	static {
		try {
			URI_ID = new UriPathElement("sites");
		} catch (HttpException e) {
			throw new RuntimeException(e);
		}
	}

	static public Site getSite(Long id) throws HttpException {
		Site site = (Site) Hiber.session().get(Site.class, id);
		if (site == null) {
			throw new UserException("There is no site with " + "that id.");
		}
		return site;
	}

	public Sites() {
	}

	public MonadUri getUri() throws HttpException {
		return Chellow.ROOT_URI.resolve(getUriId()).append("/");
	}

	public void httpPost(Invocation inv) throws HttpException {
		Document doc = MonadUtils.newSourceDocument();
		String code = inv.getString("code");
		String name = inv.getString("name");
		if (!inv.isValid()) {
			throw new UserException(doc, null);
		}
		Site site = Site.insertSite(code, name);
		Hiber.commit();
		inv.sendCreated(site.getUri());
	}

	@SuppressWarnings("unchecked")
	public void httpGet(Invocation inv) throws HttpException {
		Document doc = MonadUtils.newSourceDocument();
		Element source = doc.getDocumentElement();
		Element sitesElement = toXml(doc);
		source.appendChild(sitesElement);
		List<Site> sites = null;
		if (inv.hasParameter("search-pattern")) {
			String searchTerm = inv.getString("search-pattern");
			if (!inv.isValid()) {
				throw new UserException(doc, null);
			}
			sites = (List<Site>) Hiber
					.session()
					.createQuery(
							"from Site site where lower(site.code || ' ' || site.name) like '%' || lower(:searchTerm) || '%' order by site.code")
					.setString("searchTerm", searchTerm).setMaxResults(50)
					.list();

		} else {
			sites = (List<Site>) Hiber.session().createQuery(
					"from Site site order by site.code").setMaxResults(50)
					.list();
		}
		for (Site site : sites) {
			sitesElement.appendChild(site.toXml(doc));
		}
		inv.sendOk(doc);
	}

	public UriPathElement getUriId() {
		return URI_ID;
	}

	public Site getChild(UriPathElement uriId) throws HttpException {
		Site site = (Site) Hiber.session().createQuery(
				"from Site site where id = :siteId").setLong("siteId",
				Long.parseLong(uriId.getString())).uniqueResult();
		if (site == null) {
			throw new NotFoundException();
		}
		return site;
	}

	/*
	 * public Site getSite(String code) throws HttpException, InternalException {
	 * Site site = (Site) Hiber .session() .createQuery( "from Site site where
	 * site.organization = :organization and site.code.string = :siteCode")
	 * .setEntity("organization", organization).setString("siteCode",
	 * code.getString()).uniqueResult(); if (site == null) { throw new
	 * NotFoundException(); } return site; }
	 */
	/*
	 * public SiteNameDataSource getSiteNameDataSource() throws
	 * ProgrammerException, UserException { // find properties file if
	 * (ChellowProperties.propertiesExists(getUri().append("names/"),
	 * "names.properties")) { ChellowProperties props = new
	 * ChellowProperties(getUri().append("names/"), "names.properties"); long
	 * propsLastModified = props.getLastModified(); SiteNameDataSource source =
	 * sources.get(organization.getId()); if (source == null ||
	 * source.getValidFrom() < propsLastModified) {
	 * sources.put(organization.getId(), new CSVSiteName(organization.getId(),
	 * propsLastModified)); } } SiteNameDataSource source =
	 * sources.get(organization.getId()); if (source == null) { source =
	 * DEFAULT_SOURCE; } return source; }
	 */
	public Element toXml(Document doc) throws HttpException {
		Element element = doc.createElement("sites");
		return element;
	}
}