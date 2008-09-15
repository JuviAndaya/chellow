from net.sf.chellow.monad import Hiber, XmlTree, UserException
from net.sf.chellow.billing import Batch

batch_id = inv.getLong('batch-id')
batch = Batch.getBatch(batch_id)
if not batch.getContract().getOrganization().equals(organization):
    raise UserException("Such a batch doesn't exist in this organization")
batch_element = batch.toXml(doc, XmlTree('contract', XmlTree('provider').put('organization')))
source.appendChild(batch_element)
for invoice in Hiber.session().createQuery("from Invoice invoice where invoice.batch = :batch order by invoice.bill.account.reference").setEntity("batch", batch).list():
    batch_element.appendChild(invoice.toXml(doc, XmlTree("bill", XmlTree("account"))));