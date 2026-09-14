import '../models/ledger_entry.dart';
import '../services/database_service.dart';

/// Data access for [LedgerEntry] and its [EntryItem] lines.
class LedgerRepository {
  LedgerRepository(this._db);

  final DatabaseService _db;

  Future<List<LedgerEntry>> getForCustomer(int customerId) async {
    final db = await _db.database;
    final rows = await db.query(
      'entries',
      where: 'customer_id = ?',
      whereArgs: [customerId],
      orderBy: 'entry_date DESC, id DESC',
    );
    final entries = rows.map(LedgerEntry.fromMap).toList();

    final itemRows = await db.query(
      'entry_items',
      where: 'entry_id IN (SELECT id FROM entries WHERE customer_id = ?)',
      whereArgs: [customerId],
    );
    final itemsByEntry = <int, List<EntryItem>>{};
    for (final row in itemRows) {
      final item = EntryItem.fromMap(row);
      itemsByEntry.putIfAbsent(item.entryId!, () => []).add(item);
    }

    return entries
        .map((entry) => entry.copyWith(items: itemsByEntry[entry.id] ?? const []))
        .toList();
  }

  /// Inserts an entry and its item lines in a single transaction.
  Future<LedgerEntry> create(LedgerEntry entry) async {
    final db = await _db.database;
    return db.transaction((txn) async {
      final entryId = await txn.insert('entries', entry.toMap());
      for (final item in entry.items) {
        await txn.insert('entry_items', item.toMap()..['entry_id'] = entryId);
      }
      return entry.copyWith(id: entryId);
    });
  }

  Future<void> deleteEntry(int entryId) async {
    final db = await _db.database;
    // Item lines cascade on the foreign key.
    await db.delete('entries', where: 'id = ?', whereArgs: [entryId]);
  }
}
