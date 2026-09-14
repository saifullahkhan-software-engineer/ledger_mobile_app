import 'package:flutter/material.dart';

import '../controllers/customers_controller.dart';
import '../core/l10n/l10n_ext.dart';
import '../models/customer.dart';
import '../repositories/customer_repository.dart';
import '../services/database_service.dart';
import '../widgets/empty_state.dart';
import 'customer_form_view.dart';
import 'ledger_view.dart';

/// Customer list: the app's main screen. Shows each customer's running
/// balance and links to their ledger.
class CustomersView extends StatefulWidget {
  const CustomersView({super.key});

  @override
  State<CustomersView> createState() => _CustomersViewState();
}

class _CustomersViewState extends State<CustomersView> {
  late final CustomersController _controller;

  @override
  void initState() {
    super.initState();
    _controller = CustomersController(
      CustomerRepository(DatabaseService.instance),
    )..load();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _openForm([Customer? customer]) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => CustomerFormView(customer: customer),
      ),
    );
    await _controller.load();
  }

  Future<void> _openLedger(Customer customer) async {
    await Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => LedgerView(customer: customer)),
    );
    await _controller.load();
  }

  Future<void> _confirmDelete(Customer customer) async {
    final l10n = context.l10n;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.confirmDeleteCustomerTitle),
        content: Text(l10n.confirmDeleteCustomerBody),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l10n.delete),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    try {
      await _controller.delete(customer);
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${l10n.error}: $error')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          if (_controller.isLoading && _controller.customers.isEmpty) {
            return const Center(child: CircularProgressIndicator());
          }
          if (_controller.customers.isEmpty) {
            return EmptyState(
              icon: Icons.people_outline,
              title: l10n.noCustomers,
              message: l10n.noCustomersHint,
            );
          }
          return ListView.builder(
            padding: const EdgeInsets.only(bottom: 88),
            itemCount: _controller.customers.length,
            itemBuilder: (context, index) {
              final customer = _controller.customers[index];
              return _CustomerTile(
                customer: customer,
                onTap: () => _openLedger(customer),
                onEdit: () => _openForm(customer),
                onDelete: () => _confirmDelete(customer),
              );
            },
          );
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openForm(),
        icon: const Icon(Icons.person_add_alt_1),
        label: Text(l10n.addCustomer),
      ),
    );
  }
}

class _CustomerTile extends StatelessWidget {
  const _CustomerTile({
    required this.customer,
    required this.onTap,
    required this.onEdit,
    required this.onDelete,
  });

  final Customer customer;
  final VoidCallback onTap;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final owesMoney = customer.balance > 0;

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      elevation: 1,
      child: ListTile(
        onTap: onTap,
        leading: CircleAvatar(
          child: Text(customer.name.isNotEmpty ? customer.name[0] : '?'),
        ),
        title: Text(customer.name),
        subtitle: Text(
          customer.balance == 0
              ? l10n.settled
              : l10n.customerBalance(customer.balance),
          style: TextStyle(
            color: owesMoney
                ? theme.colorScheme.error
                : theme.colorScheme.onSurfaceVariant,
            fontWeight: owesMoney ? FontWeight.w600 : FontWeight.w400,
          ),
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              icon: const Icon(Icons.edit_outlined),
              tooltip: l10n.editCustomer,
              onPressed: onEdit,
            ),
            IconButton(
              icon: const Icon(Icons.delete_outline),
              tooltip: l10n.delete,
              onPressed: onDelete,
            ),
          ],
        ),
      ),
    );
  }
}
