from net.sf.chellow.monad import Monad
import collections
import db
import templater
Monad.getUtils()['impt'](globals(), 'db', 'utils', 'templater')
RegisterRead, Bill = db.RegisterRead, db.Bill
render = templater.render
inv, template = globals()['inv'], globals()['template']

sess = None
try:
    sess = db.session()
    bill_id = inv.getLong('mop_bill_id')
    bill = Bill.get_by_id(sess, bill_id)
    register_reads = sess.query(RegisterRead).filter(
        RegisterRead.bill_id == bill_id).order_by(
        RegisterRead.present_date.desc())
    fields = {'bill': bill, 'register_reads': register_reads}
    try:
        breakdown_dict = eval(bill.breakdown, {})

        raw_lines = []
        for key in ('raw_lines', 'raw-lines'):
            try:
                raw_lines += breakdown_dict[key]
                del breakdown_dict[key]
            except KeyError:
                pass

        rows = set()
        columns = set()
        grid = collections.defaultdict(dict)

        for k, v in breakdown_dict.items():
            if k.endswith('-gbp'):
                columns.add('gbp')
                row_name = k[:-4]
                rows.add(row_name)
                grid[row_name]['gbp'] = v
                del breakdown_dict[k]

        for k, v in breakdown_dict.items():
            for row_name in sorted(list(rows), key=len, reverse=True):
                if k.startswith(row_name + '-'):
                    col_name = k[len(row_name) + 1:]
                    columns.add(col_name)
                    grid[row_name][col_name] = v
                    del breakdown_dict[k]
                    break

        for k, v in breakdown_dict.items():
            pair = k.split('-')
            row_name = '-'.join(pair[:-1])
            column_name = pair[-1]
            rows.add(row_name)
            columns.add(column_name)
            grid[row_name][column_name] = v

        column_list = sorted(list(columns))
        for rate_name in [col for col in column_list if col.endswith('rate')]:
            column_list.remove(rate_name)
            column_list.append(rate_name)

        if 'gbp' in column_list:
            column_list.remove('gbp')
            column_list.append('gbp')

        row_list = sorted(list(rows))
        fields.update(
            {
                'raw_lines': raw_lines, 'row_list': row_list,
                'column_list': column_list, 'grid': grid})
    except SyntaxError as e:
        pass
    render(inv, template, fields)
finally:
    if sess is not None:
        sess.close()