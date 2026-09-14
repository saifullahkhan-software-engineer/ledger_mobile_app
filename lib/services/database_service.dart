import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

/// Owns the SQLite database lifecycle: opens the single local database file
/// and creates/upgrades the schema. One table per aggregate:
///
///   customers   -> one row per customer
///   entries     -> one row per credit/payment transaction
///   entry_items -> item detail lines for a credit (udhaar) entry
///
/// Amounts are stored as INTEGER in minor units (e.g. rupees * 100) to avoid
/// floating point rounding issues. All user text columns are stored exactly as
/// entered (SQLite TEXT is UTF-8 aware), so English, Urdu, or mixed input is
/// preserved without transformation.
class DatabaseService {
  DatabaseService._();

  static final DatabaseService instance = DatabaseService._();

  static const String dbFileName = 'ledger.db';
  static const int dbVersion = 1;

  Database? _db;

  Future<Database> get database async {
    _db ??= await _open();
    return _db!;
  }

  Future<Database> _open() async {
    final dbPath = await getDatabasesPath();
    return openDatabase(
      p.join(dbPath, dbFileName),
      version: dbVersion,
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );
  }

  Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE customers (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        phone TEXT,
        notes TEXT,
        created_at TEXT NOT NULL DEFAULT (datetime('now'))
      )
    ''');

    await db.execute('''
      CREATE TABLE entries (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        customer_id INTEGER NOT NULL,
        type TEXT NOT NULL CHECK (type IN ('CREDIT', 'PAYMENT')),
        amount INTEGER NOT NULL CHECK (amount > 0),
        description TEXT,
        entry_date TEXT NOT NULL,
        created_at TEXT NOT NULL DEFAULT (datetime('now')),
        FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE
      )
    ''');

    await db.execute('''
      CREATE TABLE entry_items (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        entry_id INTEGER NOT NULL,
        item_name TEXT NOT NULL,
        quantity REAL NOT NULL,
        unit TEXT NOT NULL,
        price INTEGER NOT NULL,
        FOREIGN KEY (entry_id) REFERENCES entries (id) ON DELETE CASCADE
      )
    ''');

    await db.execute('CREATE INDEX idx_entries_customer ON entries (customer_id)');
    await db.execute('CREATE INDEX idx_entry_items_entry ON entry_items (entry_id)');
  }

  Future<void> _onUpgrade(Database db, int oldVersion, int newVersion) async {
    // v1 is the initial schema; future migrations go here, guarded by version.
  }
}
