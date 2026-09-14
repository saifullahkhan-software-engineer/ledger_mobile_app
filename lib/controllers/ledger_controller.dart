import 'package:flutter/foundation.dart';

import '../core/utils/money_format.dart';
import '../models/entry_type.dart';
import '../models/ledger_entry.dart';
import '../repositories/ledger_repository.dart';

/// A line of an in-progress credit entry, before it is persisted.
class DraftItem {
  DraftItem({
    this.name = '',
    this.quantity = '',
    this.unit = '',
    this.price = '',
  });

  String name;
  String quantity;
  String unit;
  String price;

  double get parsedQuantity => double.tryParse(quantity.trim()) ?? 0;

  int get parsedPrice => MoneyFormat.parse(price) ?? 0;

  int get total => (parsedQuantity * parsedPrice).round();

  bool get isComplete =>
      name.trim().isNotEmpty &&
      quantity.trim().isNotEmpty &&
      unit.trim().isNotEmpty &&
      price.trim().isNotEmpty;

  EntryItem? toEntryItem() {
    final qty = double.tryParse(quantity.trim());
    final priceUnits = MoneyFormat.parse(price);
    if (!isComplete || qty == null || priceUnits == null) return null;
    return EntryItem(
      itemName: name.trim(),
      quantity: qty,
      unit: unit.trim(),
      price: priceUnits,
    );
  }
}

/// Validation and persistence for ledger entries, plus computed balances.
class LedgerController extends ChangeNotifier {
  LedgerController(this._repository);

  final LedgerRepository _repository;

  List<LedgerEntry> _entries = [];
  bool _isLoading = false;

  List<LedgerEntry> get entries => _entries;
  bool get isLoading => _isLoading;

  int _balanceOf(List<LedgerEntry> list) {
    var balance = 0;
    for (final entry in list) {
      balance += entry.type == EntryType.credit ? entry.amount : -entry.amount;
    }
    return balance;
  }

  int get balance => _balanceOf(_entries);

  int get totalCredit => _entries
      .where((e) => e.type == EntryType.credit)
      .fold(0, (sum, e) => sum + e.amount);

  int get totalPayments => _entries
      .where((e) => e.type == EntryType.payment)
      .fold(0, (sum, e) => sum + e.amount);

  Future<void> load(int customerId) async {
    _isLoading = true;
    notifyListeners();
    try {
      _entries = await _repository.getForCustomer(customerId);
    } catch (error) {
      debugPrint('Failed to load ledger entries: $error');
      _entries = [];
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Returns a message key for the first validation problem, or `null`.
  /// A non-null [items] list (for CREDIT) requires every line to be complete.
  String? validate({
    required String amount,
    required List<DraftItem>? items,
  }) {
    final amountUnits = MoneyFormat.parse(amount);
    if (amount.trim().isEmpty) return 'amountRequired';
    if (amountUnits == null) return 'amountInvalid';

    if (items != null) {
      for (final item in items) {
        if (!item.isComplete) return 'itemFieldsRequired';
        if (MoneyFormat.parse(item.price) == null) return 'itemPriceInvalid';
        final qty = double.tryParse(item.quantity.trim());
        if (qty == null || qty <= 0) return 'itemQuantityInvalid';
        if (item.total <= 0) return 'itemAmountInvalid';
      }
    }
    return null;
  }

  Future<LedgerEntry> create({
    required int customerId,
    required EntryType type,
    required int amount,
    String? description,
    required String entryDate,
    List<EntryItem> items = const [],
  }) async {
    final entry = LedgerEntry(
      customerId: customerId,
      type: type,
      amount: amount,
      description: (description == null || description.trim().isEmpty)
          ? null
          : description.trim(),
      entryDate: entryDate,
      items: items,
    );
    await _repository.create(entry);
    await load(customerId);
    return entry;
  }
}
