from net.sf.chellow.monad import Hiber, XmlTree, UserException
from net.sf.chellow.billing import Account

account_id = inv.getLong('account-id')
account = Account.getAccount(account_id)
if not account.getOrganization().equals(organization):
    raise UserException("Such an account doesn't exist in this organization")
bills_element = doc.createElement('bills')
source.appendChild(bills_element)
bills_element.appendChild(account.toXml(doc, XmlTree('provider')))
for bill in Hiber.session().createQuery("from Bill bill where bill.account = :account order by bill.startDate.date").setEntity('account', account).list():
    bills_element.appendChild(bill.toXml(doc))
source.appendChild(organization.toXml(doc))