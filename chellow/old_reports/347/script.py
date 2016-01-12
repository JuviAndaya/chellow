from net.sf.chellow.monad import Monad
import db
import templater
import utils
Monad.getUtils()['impt'](globals(), 'db', 'utils', 'templater')
BillType, Bill = db.BillType, db.Bill
render = templater.render
form_date, form_decimal = utils.form_date, utils.form_decimal
UserException = utils.UserException
inv, template = globals()['inv'], globals()['template']


def make_fields(sess, bill, message=None):
    bill_types = sess.query(BillType).order_by(BillType.code).all()
    messages = [] if message is None else [str(message)]
    return {'bill': bill, 'bill_types': bill_types, 'messages': messages}

sess = None
try:
    sess = db.session()
    if inv.getRequest().getMethod() == 'GET':
        bill_id = inv.getLong('hhdc_bill_id')
        bill = Bill.get_by_id(sess, bill_id)
        render(inv, template, make_fields(sess, bill))
    else:
        db.set_read_write(sess)
        bill_id = inv.getLong('hhdc_bill_id')
        bill = Bill.get_by_id(sess, bill_id)
        if inv.hasParameter('update'):
            account = inv.getString("account")
            reference = inv.getString("reference")
            issue_date = form_date(inv, "issue")
            start_date = form_date(inv, "start")
            finish_date = form_date(inv, "finish")
            kwh = form_decimal(inv, "kwh")
            net = form_decimal(inv, "net")
            vat = form_decimal(inv, "vat")
            gross = form_decimal(inv, "gross")
            type_id = inv.getLong("bill_type_id")
            breakdown = inv.getString("breakdown")
            bill_type = BillType.get_by_id(sess, type_id)

            bill.update(
                account, reference, issue_date, start_date, finish_date, kwh,
                net, vat, gross, bill_type, breakdown)
            sess.commit()
            inv.sendSeeOther(
                "/reports/345/output/?hhdc_bill_id=" + str(bill.id))
        elif inv.hasParameter("delete"):
            bill.delete(sess)
            sess.commit()
            inv.sendSeeOther(
                "/reports/203/output/?hhdc_batch_id=" + str(bill.batch.id))
except UserException as e:
    render(inv, template, make_fields(sess, bill, e))
finally:
    sess.close()