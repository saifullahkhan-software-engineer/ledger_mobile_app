import 'package:flutter/material.dart';

import '../models/app_user.dart';
import '../services/api_service.dart';
import 'add_user_view.dart';
import 'user_detail_view.dart';

/// Users & Access: list users, add managers, assign businesses, change roles
/// and verify KYC. All actions call the backend, so nothing is fake.
class ManagersView extends StatefulWidget {
  final ApiService api;
  final bool isSuperAdmin;

  const ManagersView({
    super.key,
    required this.api,
    required this.isSuperAdmin,
  });

  @override
  State<ManagersView> createState() => _ManagersViewState();
}

class _ManagersViewState extends State<ManagersView> {
  bool _loading = true;
  String? _error;

  List<AppUser> _users = [];
  List<Map<String, dynamic>> _businesses = [];
  String _filter = 'ALL';
  String _search = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        widget.api.users(),
        widget.api.businesses(),
      ]);
      if (!mounted) return;
      setState(() {
        _users = (results[0] as List)
            .map((e) => AppUser.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList();
        _businesses = (results[1] as List)
            .map((e) => Map<String, dynamic>.from(e as Map))
            .toList();
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _businessName(String id) {
    for (final b in _businesses) {
      if (b['id'] == id) return b['name'] as String? ?? id;
    }
    return id;
  }

  List<AppUser> get _filtered {
    return _users.where((u) {
      final matchesRole = _filter == 'ALL' || u.role == _filter;
      final matchesSearch = _search.trim().isEmpty ||
          u.name.toLowerCase().contains(_search.trim().toLowerCase()) ||
          u.phone.contains(_search.trim());
      return matchesRole && matchesSearch;
    }).toList();
  }

  void _showSnack(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _openAddUser() async {
    if (!widget.isSuperAdmin) return;
    final created = await Navigator.of(context).push<Map<String, dynamic>>(
      MaterialPageRoute(
        builder: (_) => AddUserScreen(
          api: widget.api,
          businesses: _businesses,
        ),
      ),
    );
    if (created != null) {
      _showSnack('${created['name']} added');
      await _load();
    }
  }

  Future<void> _openDetail(AppUser user) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => UserDetailScreen(
          api: widget.api,
          user: user,
          businesses: _businesses,
          isSuperAdmin: widget.isSuperAdmin,
        ),
      ),
    );
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Users & Access'),
        backgroundColor: const Color(0xFF1B5E20),
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loading ? null : _load,
          ),
        ],
      ),
      floatingActionButton: widget.isSuperAdmin
          ? FloatingActionButton.extended(
              backgroundColor: const Color(0xFF1B5E20),
              foregroundColor: Colors.white,
              onPressed: _openAddUser,
              icon: const Icon(Icons.person_add),
              label: const Text('Add User'),
            )
          : null,
      body: Column(
        children: [
          if (_error != null)
            Container(
              width: double.infinity,
              color: Colors.red.shade50,
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  Icon(Icons.error_outline, color: Colors.red.shade800),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      _error!,
                      style: TextStyle(color: Colors.red.shade800, fontSize: 13),
                    ),
                  ),
                  TextButton(
                    onPressed: _load,
                    child: const Text('Retry'),
                  ),
                ],
              ),
            ),

          // Search
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: TextField(
              onChanged: (v) => setState(() => _search = v),
              decoration: InputDecoration(
                hintText: 'Search by name or phone...',
                prefixIcon: const Icon(Icons.search),
                contentPadding: const EdgeInsets.symmetric(vertical: 0),
                filled: true,
                fillColor: Colors.grey.shade100,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ),

          // Role filter chips
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                _chip('ALL', 'All (${_count('ALL')})'),
                const SizedBox(width: 8),
                _chip('INVESTOR', 'Investors (${_count('INVESTOR')})'),
                const SizedBox(width: 8),
                _chip('ADMIN', 'Managers (${_count('ADMIN')})'),
                const SizedBox(width: 8),
                _chip('SUPERADMIN', 'Super Admins (${_count('SUPERADMIN')})'),
              ],
            ),
          ),

          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _filtered.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.person_off,
                                size: 48, color: Colors.grey[400]),
                            const SizedBox(height: 12),
                            Text(
                              _users.isEmpty
                                  ? 'No users on the server yet'
                                  : 'No users match your search',
                              style: TextStyle(color: Colors.grey[600]),
                            ),
                          ],
                        ),
                      )
                    : RefreshIndicator(
                        onRefresh: _load,
                        child: ListView.builder(
                          padding: const EdgeInsets.all(12),
                          itemCount: _filtered.length,
                          itemBuilder: (context, index) {
                            final user = _filtered[index];
                            return Card(
                              margin: const EdgeInsets.only(bottom: 10),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: ListTile(
                                leading: CircleAvatar(
                                  backgroundColor: _avatarBg(user.role),
                                  child: Text(
                                    user.name.isNotEmpty
                                        ? user.name[0].toUpperCase()
                                        : 'U',
                                    style: TextStyle(
                                      color: user.role == 'SUPERADMIN'
                                          ? const Color(0xFF1B5E20)
                                          : Colors.white,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                                title: Row(
                                  children: [
                                    Expanded(
                                      child: Text(
                                        user.name,
                                        style: const TextStyle(
                                            fontWeight: FontWeight.bold),
                                      ),
                                    ),
                                    _badge(user.role),
                                  ],
                                ),
                                subtitle: Column(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.start,
                                  children: [
                                    const SizedBox(height: 4),
                                    Text(
                                      user.phone,
                                      style: TextStyle(
                                          color: Colors.grey[700]),
                                    ),
                                    if (user.assignedBusinesses.isNotEmpty)
                                      Text(
                                        user.assignedBusinesses
                                            .map(_businessName)
                                            .join(', '),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: TextStyle(
                                            color: Colors.grey[600],
                                            fontSize: 12),
                                      ),
                                    Text(
                                      'KYC: ${user.kycStatus}',
                                      style: TextStyle(
                                        fontSize: 12,
                                        color: user.kycStatus == 'VERIFIED'
                                            ? Colors.green[700]
                                            : Colors.amber[800],
                                      ),
                                    ),
                                  ],
                                ),
                                trailing: const Icon(Icons.chevron_right),
                                onTap: () => _openDetail(user),
                              ),
                            );
                          },
                        ),
                      ),
          ),
        ],
      ),
    );
  }

  int _count(String role) =>
      role == 'ALL' ? _users.length : _users.where((u) => u.role == role).length;

  Color _avatarBg(String role) {
    switch (role) {
      case 'SUPERADMIN':
        return const Color(0xFFFFD700);
      case 'ADMIN':
        return Colors.blue.shade700;
      default:
        return const Color(0xFF1B5E20);
    }
  }

  Widget _badge(String role) {
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

  Widget _chip(String role, String label) {
    final selected = _filter == role;
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      selectedColor: const Color(0xFF1B5E20),
      labelStyle: TextStyle(
        color: selected ? Colors.white : Colors.black87,
        fontWeight: selected ? FontWeight.bold : FontWeight.normal,
        fontSize: 12,
      ),
      onSelected: (_) => setState(() => _filter = role),
    );
  }
}
