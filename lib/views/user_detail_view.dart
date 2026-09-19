import 'package:flutter/material.dart';

import '../models/app_user.dart';
import '../services/api_service.dart';

/// User detail + admin controls: change role, assign/remove businesses and
/// verify KYC — all persisted through the backend.
class UserDetailScreen extends StatefulWidget {
  final ApiService api;
  final AppUser user;
  final List<Map<String, dynamic>> businesses;
  final bool isSuperAdmin;

  const UserDetailScreen({
    super.key,
    required this.api,
    required this.user,
    required this.businesses,
    required this.isSuperAdmin,
  });

  @override
  State<UserDetailScreen> createState() => _UserDetailScreenState();
}

class _UserDetailScreenState extends State<UserDetailScreen> {
  late AppUser _user = widget.user;
  late final Set<String> _selected = _user.assignedBusinesses.toSet();
  bool _saving = false;
  String? _error;

  bool get _isAdmin => _user.role == 'ADMIN';

  String _businessName(String id) {
    for (final b in widget.businesses) {
      if (b['id'] == id) return b['name'] as String? ?? id;
    }
    return id;
  }

  Future<void> _run(Future<AppUser?> Function() action) async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final updated = await action();
      if (updated != null) setState(() => _user = updated);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  AppUser _fromJson(Map<String, dynamic> json) =>
      AppUser.fromJson(json);

  void _showSnack(String msg) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('User Details'),
        backgroundColor: const Color(0xFF1B5E20),
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Reload',
            onPressed: _saving
                ? null
                : () => _run(() async =>
                    _fromJson(await widget.api.user(_user.id))),
          ),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    radius: 28,
                    backgroundColor: const Color(0xFF1B5E20),
                    child: Text(
                      _user.name.isNotEmpty
                          ? _user.name[0].toUpperCase()
                          : 'U',
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 22,
                          fontWeight: FontWeight.bold),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _user.name,
                          style: const TextStyle(
                              fontSize: 20, fontWeight: FontWeight.bold),
                        ),
                        Text(
                          _user.phone,
                          style: TextStyle(color: Colors.grey[600]),
                        ),
                      ],
                    ),
                  ),
                  _roleBadge(_user.role),
                ],
              ),
              const SizedBox(height: 16),
              const Divider(),
              _row('User ID', _user.id),
              _row('Role', _user.role),
              _row('KYC Status', _user.kycStatus),
              if (_user.assignedBusinesses.isNotEmpty)
                _row(
                  'Assigned Businesses',
                  _user.assignedBusinesses.map(_businessName).join('\n'),
                ),

              if (_error != null) ...[
                const SizedBox(height: 12),
                Container(
                  width: double.infinity,
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
              ],

              if (widget.isSuperAdmin && _user.role != 'SUPERADMIN') ...[
                const SizedBox(height: 24),
                const Text(
                  'Super Admin Actions',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF1B5E20),
                  ),
                ),
                const SizedBox(height: 12),

                // Role switch
                ListTile(
                  tileColor: Colors.grey.shade50,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10)),
                  leading: const Icon(Icons.manage_accounts),
                  title: const Text('Role'),
                  trailing: DropdownButton<String>(
                    value: _user.role == 'SUPERADMIN'
                        ? 'ADMIN'
                        : _user.role,
                    items: const [
                      DropdownMenuItem(
                          value: 'INVESTOR', child: Text('Investor')),
                      DropdownMenuItem(
                          value: 'ADMIN', child: Text('Manager')),
                    ],
                    onChanged: _saving
                        ? null
                        : (role) => _run(() async {
                              final json = await widget.api.updateRole(
                                  _user.id, role!);
                              final updated = _fromJson(json);
                              _showSnack(
                                  'Role changed to ${role} for ${_user.name}');
                              return updated;
                            }),
                  ),
                ),

                // Business assignment (managers only)
                if (_isAdmin) ...[
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 6, vertical: 6),
                    decoration: BoxDecoration(
                      color: Colors.grey.shade50,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Padding(
                          padding: EdgeInsets.fromLTRB(10, 6, 0, 0),
                          child: Text(
                            'Business access',
                            style: TextStyle(fontWeight: FontWeight.bold),
                          ),
                        ),
                        ...widget.businesses.map((b) {
                          final id = b['id'] as String;
                          return CheckboxListTile(
                            value: _selected.contains(id),
                            title: Text(b['name'] as String? ?? id),
                            controlAffinity: ListTileControlAffinity.leading,
                            onChanged: _saving
                                ? null
                                : (checked) async {
                                    setState(() {
                                      checked == true
                                          ? _selected.add(id)
                                          : _selected.remove(id);
                                    });
                                    await _run(() async {
                                      await widget.api.setUserBusinesses(
                                        _user.id,
                                        _user.assignedBusinesses,
                                        _selected.toList(),
                                      );
                                      return _fromJson(
                                        await widget.api.user(_user.id),
                                      );
                                    });
                                  },
                          );
                        }),
                      ],
                    ),
                  ),
                ],

                // KYC verification (investors)
                if (_user.kycStatus != 'VERIFIED') ...[
                  const SizedBox(height: 8),
                  ListTile(
                    tileColor: Colors.green.shade50,
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10)),
                    leading: const Icon(Icons.verified_user,
                        color: Color(0xFF1B5E20)),
                    title: const Text('Verify KYC'),
                    subtitle: Text(
                        'Current: ${_user.kycStatus} — confirms identity '
                        'before investing'),
                    trailing: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF1B5E20),
                        foregroundColor: Colors.white,
                      ),
                      onPressed: _saving
                          ? null
                          : () => _run(() async {
                                final json = await widget.api
                                    .verifyKyc(_user.id);
                                final updated = _fromJson(json);
                                _showSnack('KYC verified for ${_user.name}');
                                return updated;
                              }),
                      child: const Text('Verify'),
                    ),
                  ),
                ],
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _row(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label,
              style: TextStyle(color: Colors.grey[700], fontSize: 14)),
          const SizedBox(width: 16),
          Flexible(
            child: Text(
              value,
              style:
                  const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
              textAlign: TextAlign.right,
            ),
          ),
        ],
      ),
    );
  }

  Widget _roleBadge(String role) {
    Color bg;
    Color fg;
    switch (role) {
      case 'SUPERADMIN':
        bg = const Color(0xFFFFD700).withOpacity(0.2);
        fg = const Color(0xFFB8860B);
        break;
      case 'ADMIN':
        bg = Colors.blue.withOpacity(0.15);
        fg = Colors.blue.shade800;
        break;
      default:
        bg = Colors.green.withOpacity(0.15);
        fg = Colors.green.shade800;
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        role,
        style: TextStyle(color: fg, fontWeight: FontWeight.bold, fontSize: 11),
      ),
    );
  }
}
