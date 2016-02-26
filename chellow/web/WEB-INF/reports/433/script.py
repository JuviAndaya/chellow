from net.sf.chellow.monad import Monad
import templater
import db
import utils
import g_bill_import
Monad.getUtils()['impt'](
    globals(), 'db', 'utils', 'templater', 'g_bill_import')
render = templater.render
UserException = utils.UserException
GBatch = db.GBatch
inv, template = globals()['inv'], globals()['template']


def make_fields(sess, importer, message=None):
    messages = None if message is None else [str(message)]
    g_batch = GBatch.get_by_id(sess, importer.g_batch_id)
    fields = {'g_batch': g_batch, 'messages': messages}
    if importer is not None:
        imp_fields = importer.make_fields()
        if 'successful_bills' in imp_fields and \
                len(imp_fields['successful_bills']) > 0:
            fields['successful_max_registers'] = \
                max(len(bill['reads']) for bill in
                    imp_fields['successful_bills'])
        fields.update(imp_fields)
        fields['status'] = importer.status()
    return fields

sess = None
try:
    sess = db.session()
    imp_id = inv.getLong('importer_id')
    importer = g_bill_import.get_bill_importer(imp_id)
    render(inv, template, make_fields(sess, importer))
except UserException, e:
    render(inv, template, make_fields(sess, importer, e), 400)
finally:
    if sess is not None:
        sess.close()
