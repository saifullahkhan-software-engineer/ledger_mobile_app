import 'entry_type.dart';

/// A single credit (udhaar) or payment transaction.
class LedgerEntry {
  const LedgerEntry({
    this.id,
    required this.customerId,
    required this.type,
    required this.amount,
    this.description,
    required this.entryDate,
    this.items = const [],
  });

  final int? id;
  final int customerId;
  final EntryType type;

  /// Amount in minor units, always positive. The sign is implied by [type].
  final int amount;

  final String? description;

  /// ISO-8601 date (`yyyy-MM-dd`) chosen by the user.
  final String entryDate;

  /// Item detail lines; populated for credit entries.
  final List<EntryItem> items;

  LedgerEntry copyWith({
    int? id,
    int? customerId,
    EntryType? type,
    int? amount,
    String? description,
    String? entryDate,
    List<EntryItem>? items,
  }) {
    return LedgerEntry(
      id: id ?? this.id,
      customerId: customerId ?? this.customerId,
      type: type ?? this.type,
      amount: amount ?? this.amount,
      description: description ?? this.description,
      entryDate: entryDate ?? this.entryDate,
      items: items ?? this.items,
    );
  }

  factory LedgerEntry.fromMap(Map<String, Object?> map) {
    return LedgerEntry(
      id: map['id'] as int?,
      customerId: map['customer_id'] as int,
      type: EntryType.fromDb(map['type'] as String?),
      amount: map['amount'] as int,
      description: map['description'] as String?,
      entryDate: map['entry_date'] as String,
    );
  }

  Map<String, Object?> toMap() => {
        if (id != null) 'id': id,
        'customer_id': customerId,
        'type': type.dbValue,
        'amount': amount,
        'description': description,
        'entry_date': entryDate,
      };
}

/// One line of item detail attached to a credit entry.
class EntryItem {
  const EntryItem({
    this.id,
    this.entryId,
    required this.itemName,
    required this.quantity,
    required this.unit,
    required this.price,
  });

  final int? id;
  final int? entryId;
  final String itemName;
  final double quantity;
  final String unit;

  /// Unit price in minor units.
  final int price;

  /// Line total in minor units.
  int get total => (quantity * price).round();

  factory EntryItem.fromMap(Map<String, Object?> map) {
    return EntryItem(
      id: map['id'] as int?,
      entryId: map['entry_id'] as int?,
      itemName: map['item_name'] as String,
      quantity: (map['quantity'] as num).toDouble(),
      unit: map['unit'] as String,
      price: map['price'] as int,
    );
  }

  Map<String, Object?> toMap() => {
        if (id != null) 'id': id,
        'entry_id': entryId,
        'item_name': itemName,
        'quantity': quantity,
        'unit': unit,
        'price': price,
      };
}
