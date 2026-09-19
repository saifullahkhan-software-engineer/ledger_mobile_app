import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/utils/phone_format.dart';
import '../services/api_service.dart';

/// Add User: create a manager (ADMIN) or register an investor. Managers can
/// be assigned to one or more businesses. The form scrolls up with the
/// keyboard so fields are never covered.
class AddUserScreen extends StatefulWidget {
  final ApiService api;
  final List<Map<String, dynamic>> businesses;

  const AddUserScreen({
    super.key,
    required this.api,
    required this.businesses,
  });

  @override
  State<AddUserScreen> createState() => _AddUserScreenState();
}

class _AddUserScreenState extends State<AddUserScreen> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _password = TextEditingController();

  String _role = 'ADMIN';
  final Set<String> _selectedBusinesses = {};
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    _phone.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() => _error = null);
    if (!(_formKey.currentState?.validate() ?? false)) return;

    final name = _name.text.trim();
    final phone = normalizePhone(_phone.text);
    final password = _password.text;

    setState(() => _saving = true);
    try {
      Map<String, dynamic> created;
      if (_role == 'INVESTOR') {
        created = await widget.api
            .registerInvestor(name, phone, password);
      } else {
        created = await widget.api.createManager(
          name,
          phone,
          password,
          businessIds: _selectedBusinesses.toList(),
        );
      }
      if (!mounted) return;
      Navigator.of(context).pop(created);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Add User'),
        backgroundColor: const Color(0xFF1B5E20),
        foregroundColor: Colors.white,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  'Create a user account',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 16),

                // Role selector
                SegmentedButton<String>(
                  segments: const [
                    ButtonSegment(
                      value: 'ADMIN',
                      label: Text('Manager'),
                      icon: Icon(Icons.manage_accounts),
                    ),
                    ButtonSegment(
                      value: 'INVESTOR',
                      label: Text('Investor'),
                      icon: Icon(Icons.person),
                    ),
                  ],
                  selected: {_role},
                  onSelectionChanged: (s) =>
                      setState(() => _role = s.first),
                ),
                const SizedBox(height: 16),

                TextFormField(
                  controller: _name,
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(
                    labelText: 'Full Name',
                    prefixIcon: Icon(Icons.person_outline),
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) => (v == null || v.trim().isEmpty)
                      ? 'Please enter a name'
                      : null,
                ),
                const SizedBox(height: 16),

                TextFormField(
                  controller: _phone,
                  keyboardType: TextInputType.phone,
                  textInputAction: TextInputAction.next,
                  inputFormatters: [
                    FilteringTextInputFormatter.allow(RegExp(r'[0-9+ ]')),
                  ],
                  decoration: const InputDecoration(
                    labelText: 'Phone Number',
                    hintText: '03001234567 or +923001234567',
                    prefixIcon: Icon(Icons.phone),
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) => phoneError(v ?? ''),
                ),
                const SizedBox(height: 16),

                TextFormField(
                  controller: _password,
                  obscureText: true,
                  textInputAction: TextInputAction.done,
                  decoration: const InputDecoration(
                    labelText: 'Password',
                    hintText: 'At least 10 characters',
                    prefixIcon: Icon(Icons.lock),
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) {
                    if (v == null || v.isEmpty) {
                      return 'Please enter a password';
                    }
                    if (v.length < 10) {
                      return 'Use at least 10 characters';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),

                // Business assignment (managers only)
                if (_role == 'ADMIN') ...[
                  const Text(
                    'Assign to businesses',
                    style: TextStyle(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  if (widget.businesses.isEmpty)
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.grey.shade100,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Text('No businesses configured yet.'),
                    )
                  else
                    ...widget.businesses.map((b) {
                      final id = b['id'] as String;
                      return CheckboxListTile(
                        value: _selectedBusinesses.contains(id),
                        title: Text(b['name'] as String? ?? id),
                        subtitle: Text((b['type'] as String? ?? '')
                            .toLowerCase()),
                        controlAffinity: ListTileControlAffinity.leading,
                        contentPadding: EdgeInsets.zero,
                        onChanged: (checked) {
                          setState(() {
                            if (checked == true) {
                              _selectedBusinesses.add(id);
                            } else {
                              _selectedBusinesses.remove(id);
                            }
                          });
                        },
                      );
                    }),
                  const SizedBox(height: 8),
                ],

                if (_error != null) ...[
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: Colors.red.shade50,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      _error!,
                      style:
                          TextStyle(color: Colors.red.shade800, fontSize: 13),
                    ),
                  ),
                  const SizedBox(height: 16),
                ],

                ElevatedButton(
                  onPressed: _saving ? null : _submit,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF1B5E20),
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                  ),
                  child: _saving
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            valueColor:
                                AlwaysStoppedAnimation<Color>(Colors.white),
                          ),
                        )
                      : Text(_role == 'ADMIN'
                          ? 'Create Manager'
                          : 'Create Investor'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
