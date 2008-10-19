package net.sf.chellow.physical;

import java.util.Date;
import java.util.List;

import net.sf.chellow.billing.HhdcContract;
import net.sf.chellow.monad.Hiber;
import net.sf.chellow.monad.HttpException;
import net.sf.chellow.monad.InternalException;
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

public class SiteSnags extends EntityList {
	static private final int PAGE_SIZE = 20;

	public static final UriPathElement URI_ID;

	static {
		try {
			URI_ID = new UriPathElement("site-snags");
		} catch (HttpException e) {
			throw new RuntimeException(e);
		}
	}

	HhdcContract hhdcContract;

	public SiteSnags(HhdcContract hhdcContract) {
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
		inv.sendOk(document());
	}

	@SuppressWarnings("unchecked")
	private Document document() throws HttpException {
		Document doc = MonadUtils.newSourceDocument();
		Element source = doc.getDocumentElement();
		Element snagsElement = toXml(doc);
		source.appendChild(snagsElement);
		snagsElement.appendChild(hhdcContract.toXml(doc,
				new XmlTree("party")));
		for (SiteSnag snag : (List<SiteSnag>) Hiber
				.session()
				.createQuery(
						"from SiteSnag snag where snag.dateResolved is null and snag.contract = :contract order by snag.site.code, snag.description, snag.startDate.date")
				.setEntity("contract", hhdcContract).setMaxResults(PAGE_SIZE)
				.list()) {
			snagsElement.appendChild(snag.toXml(doc, new XmlTree("site")));
		}
		source.appendChild(MonadDate.getMonthsXml(doc));
		source.appendChild(MonadDate.getDaysXml(doc));
		source.appendChild(new MonadDate().toXml(doc));
		return doc;
	}

	@SuppressWarnings("unchecked")
	public void httpPost(Invocation inv) throws HttpException {
		if (inv.hasParameter("ignore")) {
			Date ignoreDate = inv.getDate("ignore-date");

			ScrollableResults snags = Hiber
					.session()
					.createQuery(
							"from SiteSnag snag where snag.contract = :contract and snag.finishDate < :ignoreDate")
					.setEntity("contract", hhdcContract).setTimestamp(
							"ignoreDate", ignoreDate).scroll(
							ScrollMode.FORWARD_ONLY);
			while (snags.next()) {
				SiteSnag snag = (SiteSnag) snags.get(0);
				snag.resolve(true);
				Hiber.session().flush();
				Hiber.session().clear();
			}
			Hiber.commit();
			hhdcContract = HhdcContract.getHhdcContract(hhdcContract.getId());
			inv.sendOk(document());
		}
	}

	public Urlable getChild(UriPathElement urlId) throws HttpException {
		return Snag.getSnag(urlId.getString());
	}

	public MonadUri getMonadUri() throws InternalException {
		return null;
	}

	public Element toXml(Document doc) throws HttpException {
		return doc.createElement("site-snags");
	}
}
