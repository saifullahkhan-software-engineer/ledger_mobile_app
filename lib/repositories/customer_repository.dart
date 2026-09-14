import '../models/customer.dart';
import '../services/database_service.dart';

/// Data access for [Customer]. Keeps SQL out of the controllers.
class CustomerRepository {
  CustomerRepository(this._db);

  final DatabaseService _db;

  Future<List<Customer>> getAll() async {
    final db = await _db.database;
    final rows = await db.rawQuery('''
      SELECT c.*,
             COALESCE((
               SELECT SUM(CASE WHEN e.type = 'CREDIT' THEN e.amount
                               ELSE -e.amount END)
               FROM entries e
               WHERE e.customer_id = c.id
             ), 0) AS balance
      FROM customers c
      ORDER BY c.name COLLATE NOCASE
    ''');
    return rows.map(Customer.fromMap).toList();
  }

  Future<Customer?> getById(int id) async {
    final db = await _db.database;
    final rows = await db.rawQuery('''
      SELECT c.*,
             COALESCE((
               SELECT SUM(CASE WHEN e.type = 'CREDIT' THEN e.amount
                               ELSE -e.amount END)
               FROM entries e
               WHERE e.customer_id = c.id
             ), 0) AS balance
      FROM customers c
      WHERE c.id = ?
    ''', [id]);
    if (rows.isEmpty) return null;
    return Customer.fromMap(rows.first);
  }

  Future<Customer> create(Customer customer) async {
    final db = await _db.database;
    final id = await db.insert('customers', customer.toMap());
    return customer.copyWith(id: id);
  }

  Future<Customer> update(Customer customer) async {
    final db = await _db.database;
    await db.update(
      'customers',
      customer.toMap(),
      where: 'id = ?',
      whereArgs: [customer.id],
    );
    return customer;
  }

  Future<void> delete(int id) async {
    final db = await _db.database;
    // Entry/item rows cascade on the foreign key.
    await db.delete('customers', where: 'id = ?', whereArgs: [id]);
  }
}
