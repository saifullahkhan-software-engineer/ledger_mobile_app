import 'package:flutter/material.dart';

import '../services/api_service.dart';

/// Mobile Screen Icons: shows the configured icons and lets the superadmin
/// change them — by pasting a URL or uploading a file (sent to the backend).
/// Every change is persisted through the API.
class IconManagerView extends StatefulWidget {
  final ApiService api;
  final Map<String, String> currentIcons;
  final Function(String key, String imageUrl)? onIconUpdated;

  const IconManagerView({
    super.key,
    required this.api,
    required this.currentIcons,
    this.onIconUpdated,
  });

  @override
  State<IconManagerView> createState() => _IconManagerViewState();
}

class _IconManagerViewState extends State<IconManagerView> {
  late Map<String, String> _icons;
  bool _loading = true;
  bool _saving = false;
  String? _error;

  final List<Map<String, dynamic>> _iconSlots = [
    {
      'key': 'app_logo',
      'label': 'Brand & Header Logo',
      'screen': 'dashboard',
      'description':
          'Main circular AT badge shown on mobile app bar and drawer header.',
      'defaultIcon': Icons.eco,
      'color': const Color(0xFF1B5E20),
    },
    {
      'key': 'business_chicken',
      'label': 'Chicken Shop Card Icon',
      'screen': 'dashboard',
      'description': 'Icon shown on the Red Chicken Shop card (first).',
      'defaultIcon': Icons.fastfood,
      'color': Colors.red.shade700,
    },
    {
      'key': 'business_lpg',
      'label': 'LPG / Gas Business Card Icon',
      'screen': 'dashboard',
      'description': 'Icon shown on the Blue LPG / Gas card (second).',
      'defaultIcon': Icons.local_fire_department,
      'color': Colors.blue.shade700,
    },
    {
      'key': 'business_broiler',
      'label': 'Poultry Farm (Broiler) Card Icon',
      'screen': 'dashboard',
      'description': 'Icon shown on the Green Poultry Farm card (third).',
      'defaultIcon': Icons.agriculture,
      'color': Colors.green.shade700,
    },
    {
      'key': 'quick_sale',
      'label': 'Add Sale Action Icon',
      'screen': 'dashboard',
      'description': 'Icon for the "Add Sale" quick action.',
      'defaultIcon': Icons.add_circle,
      'color': const Color(0xFF1B5E20),
    },
    {
      'key': 'quick_expense',
      'label': 'Add Expense Action Icon',
      'screen': 'dashboard',
      'description': 'Icon for the "Add Expense" quick action.',
      'defaultIcon': Icons.account_balance_wallet,
      'color': Colors.orange.shade800,
    },
    {
      'key': 'quick_reports',
      'label': 'Reports Action Icon',
      'screen': 'dashboard',
      'description': 'Icon for the "Reports" quick action.',
      'defaultIcon': Icons.insert_chart,
      'color': const Color(0xFF1B5E20),
    },
    {
      'key': 'quick_stock',
      'label': 'Stock Action Icon',
      'screen': 'dashboard',
      'description': 'Icon for the "Stock" quick action.',
      'defaultIcon': Icons.inventory_2,
      'color': const Color(0xFF1B5E20),
    },
  ];

  @override
  void initState() {
    super.initState();
    _icons = Map<String, String>.from(widget.currentIcons);
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final items = await widget.api.icons();
      final map = <String, String>{};
      for (final item in items) {
        final key = item['key'] as String?;
        final url = item['image_url'] as String?;
        if (key != null) map[key] = url ?? '';
      }
      if (!mounted) return;
      setState(() => _icons = map);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _apply(
    String key,
    String label,
    String screen,
    String imageUrl,
  ) async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.api.setIcon(
        key,
        label: label,
        screen: screen,
        imageUrl: imageUrl,
      );
      if (!mounted) return;
      setState(() {
        if (imageUrl.isEmpty) {
          _icons.remove(key);
        } else {
          _icons[key] = imageUrl;
        }
      });
      widget.onIconUpdated?.call(key, imageUrl);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            imageUrl.isEmpty
                ? '$label reset to the default icon'
                : '$label updated on the mobile screen',
          ),
        ),
      );
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  String? _resolve(String? url) {
    if (url == null || url.isEmpty) return '';
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    if (url.startsWith('/')) return '${widget.api.normalizedBase}$url';
    return url;
  }

  void _openEditDialog(Map<String, dynamic> slot) {
    final key = slot['key'] as String;
    final label = slot['label'] as String;
    final screen = slot['screen'] as String;
    final currentUrl = _icons[key] ?? '';

    showDialog(
      context: context,
      builder: (ctx) => _IconDialog(
        slot: slot,
        currentUrl: currentUrl,
        busy: _saving,
        isImageUrl: _isImageUrl,
        resolveUrl: _resolve,
        onSave: (url) {
          Navigator.pop(ctx);
          _apply(key, label, screen, url);
        },
      ),
    );
  }

  bool _isImageUrl(String value) {
    final v = value.trim().toLowerCase();
    return v.endsWith('.png') ||
        v.endsWith('.jpg') ||
        v.endsWith('.jpeg') ||
        v.endsWith('.webp') ||
        v.endsWith('.svg') ||
        v.endsWith('.ico') ||
        v.contains('unsplash.com') ||
        v.contains('http://') ||
        v.contains('https://');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Mobile Screen Icons'),
        backgroundColor: const Color(0xFF1B5E20),
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loading ? null : _load,
          ),
        ],
      ),
      body: Column(
        children: [
          if (_error != null)
            Container(
              width: double.infinity,
              color: Colors.red.shade50,
              padding: const EdgeInsets.all(12),
              child: Text(
                _error!,
                style: TextStyle(color: Colors.red.shade800, fontSize: 13),
              ),
            ),
          Container(
            padding: const EdgeInsets.all(16),
            color: const Color(0xFF1B5E20).withOpacity(0.06),
            child: Row(
              children: [
                const Icon(Icons.info_outline, color: Color(0xFF1B5E20)),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Super Admin: pick or upload an image for each card, '
                    'app bar logo and quick action. Changes save to the server.',
                    style: TextStyle(color: Colors.grey[800], fontSize: 13),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : ListView.builder(
                    padding: const EdgeInsets.all(14),
                    itemCount: _iconSlots.length,
                    itemBuilder: (context, index) {
                      final slot = _iconSlots[index];
                      final key = slot['key'] as String;
                      final customImg = _icons[key];
                      final hasCustom = customImg != null && customImg.isNotEmpty;
                      return Card(
                        margin: const EdgeInsets.only(bottom: 12),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                          side: BorderSide(
                            color: hasCustom
                                ? const Color(0xFFFFD700)
                                : Colors.grey.withOpacity(0.2),
                            width: hasCustom ? 2 : 1,
                          ),
                        ),
                        child: ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16, vertical: 10),
                          leading: Container(
                            width: 52,
                            height: 52,
                            decoration: BoxDecoration(
                              color: slot['color'] as Color,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Center(
                              child: hasCustom
                                  ? ClipRRect(
                                      borderRadius: BorderRadius.circular(10),
                                      child: Image.network(
                                        _resolve(customImg),
                                        width: 48,
                                        height: 48,
                                        fit: BoxFit.cover,
                                        errorBuilder: (_, __, ___) => Icon(
                                          slot['defaultIcon'] as IconData,
                                          color: Colors.white,
                                          size: 26,
                                        ),
                                      ),
                                    )
                                  : Icon(
                                      slot['defaultIcon'] as IconData,
                                      color: Colors.white,
                                      size: 26,
                                    ),
                            ),
                          ),
                          title: Row(
                            children: [
                              Expanded(
                                child: Text(
                                  slot['label'] as String,
                                  style: const TextStyle(
                                      fontWeight: FontWeight.bold),
                                ),
                              ),
                              if (hasCustom)
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                      horizontal: 6, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: const Color(0xFFFFD700)
                                        .withOpacity(0.2),
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: const Text(
                                    'CUSTOM',
                                    style: TextStyle(
                                      fontSize: 10,
                                      fontWeight: FontWeight.bold,
                                      color: Color(0xFFB8860B),
                                    ),
                                  ),
                                ),
                            ],
                          ),
                          subtitle: Text(
                            hasCustom ? customImg : 'Using default system icon',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color:
                                  hasCustom ? Colors.blue[700] : Colors.grey[500],
                              fontSize: 11,
                            ),
                          ),
                          trailing: ElevatedButton.icon(
                            style: ElevatedButton.styleFrom(
                              backgroundColor: const Color(0xFF1B5E20),
                              foregroundColor: Colors.white,
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 12, vertical: 8),
                            ),
                            icon: const Icon(Icons.add_photo_alternate,
                                size: 16),
                            label: Text(hasCustom ? 'Change' : 'Add Image'),
                            onPressed: () => _openEditDialog(slot),
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

class _IconDialog extends StatefulWidget {
  final Map<String, dynamic> slot;
  final String currentUrl;
  final bool busy;
  final bool Function(String value) isImageUrl;
  final String Function(String? url) resolveUrl;
  final void Function(String url) onSave;

  const _IconDialog({
    required this.slot,
    required this.currentUrl,
    required this.busy,
    required this.isImageUrl,
    required this.resolveUrl,
    required this.onSave,
  });

  @override
  State<_IconDialog> createState() => _IconDialogState();
}

class _IconDialogState extends State<_IconDialog> {
  late final TextEditingController _controller =
      TextEditingController(text: widget.currentUrl);
  late String _previewUrl = widget.currentUrl;
  bool _uploading = false;
  String? _error;

  final List<Map<String, dynamic>> _presets = [
    {
      'url': 'https://images.unsplash.com/photo-1548550023-2bdb3c5beed7?w=150',
      'label': 'Chicken',
    },
    {
      'url': 'https://images.unsplash.com/photo-1584281722572-c283838bb892?w=150',
      'label': 'Gas/Industrial',
    },
    {
      'url': 'https://images.unsplash.com/photo-1516467508483-a7212febe31a?w=150',
      'label': 'Farm/Broiler',
    },
    {
      'url': 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150',
      'label': 'Logo abstract',
    },
  ];

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  bool get _valueIsImage => widget.isImageUrl(widget.resolveUrl(_previewUrl));

  String get _resolvedPreview => widget.resolveUrl(_previewUrl);

  @override
  Widget build(BuildContext context) {
    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(context).size.height * 0.85,
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.image_search,
                        color: Color(0xFF1B5E20)),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Set Image for ${widget.slot['label']}',
                        style: const TextStyle(fontSize: 18),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  widget.slot['description'] as String,
                  style: TextStyle(color: Colors.grey[600], fontSize: 13),
                ),
                const SizedBox(height: 16),
                const Text(
                  'Live Preview:',
                  style:
                      TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                ),
                const SizedBox(height: 8),
                Center(
                  child: Container(
                    width: 80,
                    height: 80,
                    decoration: BoxDecoration(
                      color: widget.slot['color'] as Color,
                      borderRadius: BorderRadius.circular(14),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.15),
                          blurRadius: 6,
                          offset: const Offset(0, 3),
                        ),
                      ],
                    ),
                    child: Center(
                      child: _valueIsImage
                          ? ClipRRect(
                              borderRadius: BorderRadius.circular(12),
                              child: Image.network(
                                _resolvedPreview,
                                width: 72,
                                height: 72,
                                fit: BoxFit.cover,
                                errorBuilder: (_, __, ___) => Icon(
                                  widget.slot['defaultIcon'] as IconData,
                                  color: Colors.white,
                                  size: 36,
                                ),
                              ),
                            )
                          : Icon(
                              widget.slot['defaultIcon'] as IconData,
                              color: Colors.white,
                              size: 36,
                            ),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _controller,
                  keyboardType: TextInputType.url,
                  decoration: InputDecoration(
                    labelText: 'Image URL',
                    hintText: 'https://... (a browser-visible image)',
                    border: const OutlineInputBorder(),
                    suffixIcon: IconButton(
                      icon: const Icon(Icons.clear),
                      onPressed: () {
                        _controller.clear();
                        setState(() {
                          _previewUrl = '';
                          _error = null;
                        });
                      },
                    ),
                  ),
                  onChanged: (v) => setState(() => _previewUrl = v.trim()),
                ),
                const SizedBox(height: 6),
                Text(
                  _valueIsImage
                      ? 'Tip: for icons saved/uploaded via this app, the local '
                          'server may not be reachable from this screen. Prefer '
                          'a public image URL.'
                      : 'Must start with http(s):// — uploads work only where '
                          'you can pick a file (desktop).',
                  style: TextStyle(color: Colors.grey[500], fontSize: 11),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Quick Presets:',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 12),
                ),
                const SizedBox(height: 6),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: _presets
                      .map((p) => InkWell(
                            onTap: () {
                              setState(() {
                                _controller.text = p['url'] as String;
                                _previewUrl = p['url'] as String;
                                _error = null;
                              });
                            },
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                ClipRRect(
                                  borderRadius: BorderRadius.circular(8),
                                  child: Image.network(
                                    p['url'] as String,
                                    width: 44,
                                    height: 44,
                                    fit: BoxFit.cover,
                                    errorBuilder: (_, __, ___) => Container(
                                      width: 44,
                                      height: 44,
                                      color: Colors.grey[300],
                                      child: const Icon(Icons.photo, size: 20),
                                    ),
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  p['label'] as String,
                                  style: const TextStyle(fontSize: 10),
                                ),
                              ],
                            ),
                          ))
                      .toList(),
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
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    TextButton(
                      onPressed: _uploading
                          ? null
                          : () => Navigator.of(context).pop(),
                      child: const Text('Cancel'),
                    ),
                    const SizedBox(width: 8),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF1B5E20),
                        foregroundColor: Colors.white,
                      ),
                      onPressed: (widget.busy || _uploading)
                          ? null
                          : () {
                              if (widget
                                  .isImageUrl(widget.resolveUrl(_previewUrl))) {
                                widget.onSave(
                                    widget.resolveUrl(_previewUrl));
                              } else {
                                setState(() =>
                                    _error = 'Enter a valid image URL');
                              }
                            },
                      child: const Text('Apply to Mobile Screen'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
