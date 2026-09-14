import 'package:flutter/material.dart';

import '../controllers/ledger_controller.dart';
import '../core/l10n/l10n_ext.dart';
import '../core/utils/money_format.dart';
import '../models/customer.dart';
import '../models/entry_type.dart';
import '../models/ledger_entry.dart';
import '../repositories/ledger_repository.dart';
import '../services/database_service.dart';

/// Add-credit / receive-payment form. For CREDIT entries the item detail
/// section is required; for PAYMENT entries the amount is the only required
/// numeric field. Item name, unit, and descriptions accept any script.
class EntryFormView extends StatefulWidget {
  const EntryFormView({
    super.key,
    required this.customer,
    required this.type,
  });

  final Customer customer;
  final EntryType type;

  @override
  State<EntryFormView> createState() => _EntryFormViewState();
}

class _EntryFormViewState extends State<EntryFormView> {
  late final TextEditingController _amountController;
  late final TextEditingController _descriptionController;
  late final LedgerController _controller;
  late DateTime _date;
  final List<_ItemRow> _items = [];
  bool _saving = false;

  bool get _isCredit => widget.type == EntryType.credit;

  @override
  void initState() {
    super.initState();
    _amountController = TextEditingController();
    _descriptionController = TextEditingController();
    _controller = LedgerController(
      LedgerRepository(DatabaseService.instance),
    );
    _date = DateTime.now();
    if (_isCredit) _addItem();
  }

  @override
  void dispose() {
    _amountController.dispose();
    _descriptionController.dispose();
    for (final item in _items) {
      item.dispose();
    }
    _controller.dispose();
    super.dispose();
  }

  void _addItem() {
    setState(() => _items.add(_ItemRow()));
  }

  void _removeItem(_ItemRow row) {
    setState(() {
      _items.remove(row);
      row.dispose();
    });
  }

  int get _itemsTotal {
    var total = 0;
    for (final item in _items) {
      total += item.draft.total;
    }
    return total;
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 1)),
    );
    if (picked != null) {
      setState(() => _date = picked);
    }
  }

  String get _isoDate =>
      '${_date.year.toString().padLeft(4, '0')}-'
      '${_date.month.toString().padLeft(2, '0')}-'
      '${_date.day.toString().padLeft(2, '0')}';

  Future<void> _save() async {
    final l10n = context.l10n;
    final amountUnits = MoneyFormat.parse(_amountController.text);
    final items = _isCredit ? _items.map((row) => row.draft).toList() : null;
    final errorKey = _controller.validate(
      amount: _amountController.text,
      items: items,
    );
    if (errorKey != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_resolveError(errorKey))),
      );
      return;
    }

    setState(() => _saving = true);
    try {
      final List<EntryItem> itemModels = _isCredit
          ? _items.map((row) => row.draft.toEntryItem()!).toList()
          : const [];
      await _controller.create(
        customerId: widget.customer.id!,
        type: widget.type,
        amount: amountUnits!,
        description: _descriptionController.text,
        entryDate: _isoDate,
        items: itemModels,
      );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (error) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${l10n.error}: $error')),
      );
    }
  }

  String _resolveError(String key) {
    final l10n = context.l10n;
    switch (key) {
      case 'amountRequired':
        return l10n.amountRequired;
      case 'amountInvalid':
        return l10n.amountInvalid;
      case 'itemFieldsRequired':
        return l10n.itemFieldsRequired;
      case 'itemPriceInvalid':
        return l10n.itemPriceInvalid;
      case 'itemQuantityInvalid':
        return l10n.itemQuantityInvalid;
      case 'itemAmountInvalid':
        return l10n.itemAmountInvalid;
      default:
        return l10n.error;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(
        title: Text(_isCredit ? l10n.addCredit : l10n.receivePayment),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
            TextFormField(
              controller: _amountController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: l10n.amount,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _descriptionController,
              maxLines: 2,
              decoration: InputDecoration(
                labelText: l10n.description,
                hintText: l10n.optional,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            InkWell(
              onTap: _pickDate,
              borderRadius: BorderRadius.circular(4),
              child: InputDecorator(
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  suffixIcon: Icon(Icons.calendar_today_outlined),
                ),
                child: Text(
                  _isSameDay(_date)
                      ? '${l10n.today} · $_isoDate'
                      : _isoDate,
                ),
              ),
            ),
            if (_isCredit) ...[
              const SizedBox(height: 24),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(l10n.items, style: Theme.of(context).textTheme.titleMedium),
                  TextButton.icon(
                    onPressed: _addItem,
                    icon: const Icon(Icons.add),
                    label: Text(l10n.addItem),
                  ),
                ],
              ),
              for (final item in _items)
                _ItemCard(
                  row: item,
                  onRemove: () => _removeItem(item),
                ),
              const SizedBox(height: 8),
              Text(
                l10n.confirmationText(_itemsTotal),
                style: Theme.of(context).textTheme.titleSmall,
                textAlign: TextAlign.end,
              ),
            ] else
              Text(
                l10n.itemsHint,
                style: Theme.of(context).textTheme.bodySmall,
              ),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: _saving ? null : _save,
            child: _saving
                ? const SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Text(l10n.save),
          ),
        ],
      ),
    );
  }

  bool _isSameDay(DateTime date) {
    final now = DateTime.now();
    return date.year == now.year &&
        date.month == now.month &&
        date.day == now.day;
  }
}

/// Wraps the four text controllers for one item line.
class _ItemRow {
  final TextEditingController name = TextEditingController();
  final TextEditingController quantity = TextEditingController();
  final TextEditingController unit = TextEditingController();
  final TextEditingController price = TextEditingController();

  DraftItem get draft => DraftItem(
        name: name.text,
        quantity: quantity.text,
        unit: unit.text,
        price: price.text,
      );

  void dispose() {
    name.dispose();
    quantity.dispose();
    unit.dispose();
    price.dispose();
  }
}

class _ItemCard extends StatelessWidget {
  const _ItemCard({required this.row, required this.onRemove});

  final _ItemRow row;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 8),
      elevation: 1,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    l10n.itemName,
                    style: Theme.of(context).textTheme.labelMedium,
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.delete_outline),
                  tooltip: l10n.removeItem,
                  onPressed: onRemove,
                ),
              ],
            ),
            TextField(
              controller: row.name,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                isDense: true,
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: row.quantity,
                    keyboardType:
                        const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      labelText: l10n.quantity,
                      border: const OutlineInputBorder(),
                      isDense: true,
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: row.unit,
                    decoration: InputDecoration(
                      labelText: l10n.unit,
                      border: const OutlineInputBorder(),
                      isDense: true,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            TextField(
              controller: row.price,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: l10n.price,
                border: const OutlineInputBorder(),
                isDense: true,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
