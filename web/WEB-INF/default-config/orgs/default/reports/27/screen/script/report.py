from net.sf.chellow.monad import Hiber, XmlTree
from net.sf.chellow.physical import ProfileClass

pc_id = inv.getLong('pc-id')
pc = ProfileClass.getProfileClass(pc_id)
source.appendChild(pc.toXml(doc))
source.appendChild(organization.toXml(doc))