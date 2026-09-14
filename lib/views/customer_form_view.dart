import 'package:flutter/material.dart';

import '../controllers/customers_controller.dart';
import '../core/l10n/l10n_ext.dart';
import '../models/customer.dart';
import '../repositories/customer_repository.dart';
import '../services/database_service.dart';

/// Add/edit form for a customer. Text fields accept English, Urdu, or mixed
/// input and persist it exactly as typed.
class CustomerFormView extends StatefulWidget {
  const CustomerFormView({super.key, this.customer});

  final Customer? customer;

  @override
  State<CustomerFormView> createState() => _CustomerFormViewState();
}

class _CustomerFormViewState extends State<CustomerFormView> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  late final TextEditingController _phoneController;
  late final TextEditingController _notesController;
  late final CustomersController _controller;
  bool _saving = false;

  bool get _isEditing => widget.customer != null;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.customer?.name ?? '');
    _phoneController = TextEditingController(text: widget.customer?.phone ?? '');
    _notesController = TextEditingController(text: widget.customer?.notes ?? '');
    _controller = CustomersController(
      CustomerRepository(DatabaseService.instance),
    );
  }

  @override
  void dispose() {
    _nameController.dispose();
    _phoneController.dispose();
    _notesController.dispose();
    _controller.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final l10n = context.l10n;
    if (!_formKey.currentState!.validate()) return;

    setState(() => _saving = true);
    try {
      await _controller.save(
        id: widget.customer?.id,
        name: _nameController.text,
        phone: _phoneController.text,
        notes: _notesController.text,
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

  String? _resolveError(String? key) {
    if (key == null) return null;
    final l10n = context.l10n;
    switch (key) {
      case 'nameRequired':
        return l10n.nameRequired;
      case 'invalidPhone':
        return l10n.invalidPhone;
      default:
        return l10n.error;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(
        title: Text(_isEditing ? l10n.editCustomer : l10n.addCustomer),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _nameController,
              textInputAction: TextInputAction.next,
              decoration: InputDecoration(
                labelText: l10n.customerName,
                border: const OutlineInputBorder(),
              ),
              validator: (_) => _resolveError(
                _controller.validate(
                  name: _nameController.text,
                  phone: _phoneController.text,
                ),
              ),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _phoneController,
              keyboardType: TextInputType.phone,
              textInputAction: TextInputAction.next,
              decoration: InputDecoration(
                labelText: l10n.phone,
                hintText: l10n.optional,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _notesController,
              maxLines: 3,
              decoration: InputDecoration(
                labelText: l10n.notes,
                hintText: l10n.optional,
                border: const OutlineInputBorder(),
              ),
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
      ),
    );
  }
}
