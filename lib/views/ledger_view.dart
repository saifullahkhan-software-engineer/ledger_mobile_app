import 'package:flutter/material.dart';

import '../controllers/ledger_controller.dart';
import '../core/l10n/l10n_ext.dart';
import '../core/utils/money_format.dart';
import '../models/customer.dart';
import '../models/entry_type.dart';
import '../models/ledger_entry.dart';
import '../repositories/ledger_repository.dart';
import '../services/database_service.dart';
import '../widgets/empty_state.dart';
import 'entry_form_view.dart';

/// A single customer's ledger: summary cards (balance, credit, payments) and
/// the transaction history.
class LedgerView extends StatefulWidget {
  const LedgerView({super.key, required this.customer});

  final Customer customer;

  @override
  State<LedgerView> createState() => _LedgerViewState();
}

class _LedgerViewState extends State<LedgerView> {
  late final LedgerController _controller;

  @override
  void initState() {
    super.initState();
    _controller = LedgerController(
      LedgerRepository(DatabaseService.instance),
    )..load(widget.customer.id!);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _openEntryForm(EntryType type) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => EntryFormView(
          customer: widget.customer,
          type: type,
        ),
      ),
    );
    await _controller.load(widget.customer.id!);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(widget.customer.name)),
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          if (_controller.isLoading && _controller.entries.isEmpty) {
            return const Center(child: CircularProgressIndicator());
          }
          return ListView(
            padding: const EdgeInsets.only(bottom: 96),
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                child: Text(
                  l10n.currentBalance,
                  style: Theme.of(context).textTheme.labelLarge,
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Text(
                  MoneyFormat.format(_controller.balance),
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        color: _controller.balance > 0
                            ? Theme.of(context).colorScheme.error
                            : Theme.of(context).colorScheme.primary,
                        fontWeight: FontWeight.bold,
                      ),
                ),
              ),
              const SizedBox(height: 12),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Row(
                  children: [
                    Expanded(
                      child: _SummaryCard(
                        label: l10n.totalCredit,
                        amount: _controller.totalCredit,
                      ),
                    ),
                    Expanded(
                      child: _SummaryCard(
                        label: l10n.totalPayments,
                        amount: _controller.totalPayments,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Text(
                  l10n.transactionHistory,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              const SizedBox(height: 8),
              if (_controller.entries.isEmpty)
                EmptyState(
                  icon: Icons.receipt_long_outlined,
                  title: l10n.noEntries,
                  message: l10n.noEntriesHint,
                )
              else
                ..._controller.entries.map(
                  (entry) => _EntryTile(entry: entry),
                ),
            ],
          );
        },
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
          child: Row(
            children: [
              Expanded(
                child: FilledButton.icon(
                  onPressed: () => _openEntryForm(EntryType.credit),
                  icon: const Icon(Icons.add),
                  label: Text(l10n.addCredit),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _openEntryForm(EntryType.payment),
                  icon: const Icon(Icons.payments_outlined),
                  label: Text(l10n.receive),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.label, required this.amount});

  final String label;
  final int amount;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      elevation: 1,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: theme.textTheme.labelMedium),
            const SizedBox(height: 4),
            Text(
              MoneyFormat.format(amount),
              style: theme.textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EntryTile extends StatelessWidget {
  const _EntryTile({required this.entry});

  final LedgerEntry entry;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final isCredit = entry.type == EntryType.credit;

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      elevation: 1,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            CircleAvatar(
              radius: 20,
              backgroundColor: isCredit
                  ? theme.colorScheme.errorContainer
                  : theme.colorScheme.primaryContainer,
              child: Icon(
                isCredit ? Icons.add : Icons.remove,
                color: isCredit
                    ? theme.colorScheme.onErrorContainer
                    : theme.colorScheme.onPrimaryContainer,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    isCredit ? l10n.credit : l10n.payment,
                    style: theme.textTheme.titleSmall,
                  ),
                  if (entry.description != null &&
                      entry.description!.isNotEmpty)
                    Text(
                      entry.description!,
                      style: theme.textTheme.bodySmall,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  if (entry.items.isNotEmpty)
                    Text(
                      _itemsSummary(entry.items),
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  '${isCredit ? '+' : '-'}${MoneyFormat.format(entry.amount)}',
                  style: theme.textTheme.titleMedium?.copyWith(
                    color: isCredit
                        ? theme.colorScheme.error
                        : theme.colorScheme.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                Text(
                  entry.entryDate,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  String _itemsSummary(List<EntryItem> items) {
    final names = items.map((item) => item.itemName).take(3).join(', ');
    return items.length > 3 ? '$names …' : names;
  }
}
