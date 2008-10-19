/*
 
 Copyright 2008 Meniscus Systems Ltd
 
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

import net.sf.chellow.billing.HhdcContract;
import net.sf.chellow.monad.Hiber;
import net.sf.chellow.monad.HttpException;
import net.sf.chellow.monad.Invocation;
import net.sf.chellow.monad.MonadUtils;
import net.sf.chellow.monad.Urlable;
import net.sf.chellow.monad.XmlTree;
import net.sf.chellow.monad.types.MonadDate;
import net.sf.chellow.monad.types.MonadUri;
import net.sf.chellow.monad.types.UriPathElement;

import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ChannelSnags extends EntityList {
	static private final int PAGE_SIZE = 20;

	public static final UriPathElement URI_ID;

	static {
		try {
			URI_ID = new UriPathElement("channel-snags");
		} catch (HttpException e) {
			throw new RuntimeException(e);
		}
	}

	HhdcContract hhdcContract;

	public ChannelSnags(HhdcContract hhdcContract) {
		this.hhdcContract = hhdcContract;
	}

	public UriPathElement getUriId() {
		return URI_ID;
	}

	public MonadUri getUri() throws HttpException {
		return hhdcContract.getUri().resolve(getUriId()).append("/");
	}

	@SuppressWarnings("unchecked")
	public void httpGet(Invocation inv) throws HttpException {
		Document doc = MonadUtils.newSourceDocument();
		Element source = doc.getDocumentElement();
		Element snagsElement = toXml(doc);
		source.appendChild(snagsElement);
		snagsElement.appendChild(hhdcContract.toXml(doc, new XmlTree("party")));
		for (ChannelSnag snag : (List<ChannelSnag>) Hiber
				.session()
				.createQuery(
						"from ChannelSnag snag where snag.dateResolved is null and snag.contract = :contract order by snag.channel.supplyGeneration.supply.id, snag.channel.supplyGeneration.id, snag.channel.isImport, snag.channel.isKwh, snag.description, snag.startDate.date")
				.setEntity("contract", hhdcContract).setMaxResults(PAGE_SIZE)
				.list()) {
			snagsElement.appendChild(snag.toXml(doc, new XmlTree("channel",
					new XmlTree("supplyGeneration", new XmlTree("supply")))));
		}
		source.appendChild(MonadDate.getMonthsXml(doc));
		source.appendChild(MonadDate.getDaysXml(doc));
		source.appendChild(new MonadDate().toXml(doc));
		inv.sendOk(doc);
	}

	@SuppressWarnings("unchecked")
	public void httpPost(Invocation inv) throws HttpException {
		if (inv.hasParameter("ignore")) {
			MonadDate ignoreDate = inv.getMonadDate("ignore-date");
			ScrollableResults snags = Hiber
					.session()
					.createQuery(
							"from ChannelSnag snag where snag.contract = :contract and snag.finishDate < :ignoreDate")
					.setEntity("contract", hhdcContract).setTimestamp(
							"ignoreDate", ignoreDate.getDate()).scroll(
							ScrollMode.FORWARD_ONLY);
			while (snags.next()) {
				ChannelSnag snag = (ChannelSnag) snags.get(0);
				snag.resolve(true);
				Hiber.session().flush();
				Hiber.session().clear();
			}
			Hiber.commit();
			inv.sendSeeOther(getUri());
		}
	}

	public Urlable getChild(UriPathElement urlId) throws HttpException {
		return Snag.getSnag(urlId.getString());
	}

	public Element toXml(Document doc) throws HttpException {
		return doc.createElement("channel-snags");
	}
}
