import 'package:flutter/material.dart';
import '../models/screen_icon.dart';
import '../widgets/custom_icons.dart';

class IconManagerView extends StatefulWidget {
  final Map<String, String> currentIcons;
  final Function(String key, String imageUrl)? onIconUpdated;

  const IconManagerView({
    super.key,
    required this.currentIcons,
    this.onIconUpdated,
  });

  @override
  State<IconManagerView> createState() => _IconManagerViewState();
}

class _IconManagerViewState extends State<IconManagerView> {
  late Map<String, String> _icons;

  final List<Map<String, dynamic>> _iconSlots = [
    {
      'key': 'app_logo',
      'label': 'Brand & Header Logo',
      'screen': 'App Bar & Drawer',
      'description': 'Main circular AT badge shown on mobile app bar and drawer header.',
      'defaultIcon': Icons.eco,
      'color': const Color(0xFF1B5E20),
    },
    {
      'key': 'business_chicken',
      'label': 'Chicken Shop Card Icon',
      'screen': 'Dashboard Card',
      'description': 'Icon shown on the Red Chicken Shop card on the mobile dashboard.',
      'defaultIcon': Icons.fastfood,
      'color': Colors.red.shade700,
    },
    {
      'key': 'business_broiler',
      'label': 'Broiler Farming Card Icon',
      'screen': 'Dashboard Card',
      'description': 'Icon shown on the Green Broiler Farming card on the mobile dashboard.',
      'defaultIcon': Icons.agriculture,
      'color': Colors.green.shade700,
    },
    {
      'key': 'business_lpg',
      'label': 'LPG Business Card Icon',
      'screen': 'Dashboard Card',
      'description': 'Icon shown on the Blue LPG cylinder card on the mobile dashboard.',
      'defaultIcon': Icons.local_fire_department,
      'color': Colors.blue.shade700,
    },
    {
      'key': 'quick_sale',
      'label': 'Add Sale Action Icon',
      'screen': 'Dashboard Quick Actions',
      'description': 'Icon for the "Add Sale" tile on mobile home screen.',
      'defaultIcon': Icons.add_circle,
      'color': const Color(0xFF1B5E20),
    },
    {
      'key': 'quick_expense',
      'label': 'Add Expense Action Icon',
      'screen': 'Dashboard Quick Actions',
      'description': 'Icon for the "Add Expense" tile on mobile home screen.',
      'defaultIcon': Icons.account_balance_wallet,
      'color': Colors.orange.shade800,
    },
    {
      'key': 'quick_reports',
      'label': 'Reports Action Icon',
      'screen': 'Dashboard Quick Actions',
      'description': 'Icon for the "Reports" tile on mobile home screen.',
      'defaultIcon': Icons.insert_chart,
      'color': const Color(0xFF1B5E20),
    },
    {
      'key': 'quick_stock',
      'label': 'Stock Action Icon',
      'screen': 'Dashboard Quick Actions',
      'description': 'Icon for the "Stock" tile on mobile home screen.',
      'defaultIcon': Icons.inventory_2,
      'color': const Color(0xFF1B5E20),
    },
  ];

  @override
  void initState() {
    super.initState();
    _icons = Map<String, String>.from(widget.currentIcons);
  }

  void _openEditDialog(Map<String, dynamic> slot) {
    final key = slot['key'] as String;
    final label = slot['label'] as String;
    final currentUrl = _icons[key] ?? '';
    final controller = TextEditingController(text: currentUrl);
    String previewUrl = currentUrl;

    // Presets that super admin can instantly pick or enter custom URL
    final presets = [
      'https://images.unsplash.com/photo-1548550023-2bdb3c5beed7?w=150', // Chicken
      'https://images.unsplash.com/photo-1516467508483-a7212febe31a?w=150', // Farm/Broiler
      'https://images.unsplash.com/photo-1584281722572-c283838bb892?w=150', // Gas/Industrial
      'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150', // Brand logo abstract
    ];

    showDialog(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              title: Row(
                children: [
                  Icon(Icons.image_search, color: const Color(0xFF1B5E20)),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Set Image for $label',
                      style: const TextStyle(fontSize: 18),
                    ),
                  ),
                ],
              ),
              content: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      slot['description'] as String,
                      style: TextStyle(color: Colors.grey[600], fontSize: 13),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'Live Preview on Mobile Screen:',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                    ),
                    const SizedBox(height: 8),
                    Center(
                      child: Container(
                        width: 80,
                        height: 80,
                        decoration: BoxDecoration(
                          color: slot['color'] as Color,
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
                          child: previewUrl.isNotEmpty
                              ? ClipRRect(
                                  borderRadius: BorderRadius.circular(12),
                                  child: Image.network(
                                    previewUrl,
                                    width: 72,
                                    height: 72,
                                    fit: BoxFit.cover,
                                    errorBuilder: (context, error, stackTrace) =>
                                        Icon(slot['defaultIcon'] as IconData, color: Colors.white, size: 36),
                                  ),
                                )
                              : Icon(slot['defaultIcon'] as IconData, color: Colors.white, size: 36),
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: controller,
                      decoration: InputDecoration(
                        labelText: 'Image URL or Asset Path',
                        hintText: 'https://... or /uploads/icons/...',
                        border: const OutlineInputBorder(),
                        suffixIcon: IconButton(
                          icon: const Icon(Icons.clear),
                          onPressed: () {
                            controller.clear();
                            setDialogState(() => previewUrl = '');
                          },
                        ),
                      ),
                      onChanged: (val) {
                        setDialogState(() => previewUrl = val.trim());
                      },
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      'Quick Presets / Samples:',
                      style: TextStyle(fontWeight: FontWeight.w600, fontSize: 12),
                    ),
                    const SizedBox(height: 6),
                    Wrap(
                      spacing: 8,
                      children: presets.map((url) {
                        return InkWell(
                          onTap: () {
                            controller.text = url;
                            setDialogState(() => previewUrl = url);
                          },
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: Image.network(
                              url,
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
                        );
                      }).toList(),
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Cancel'),
                ),
                if (currentUrl.isNotEmpty)
                  TextButton(
                    onPressed: () {
                      setState(() {
                        _icons.remove(key);
                      });
                      widget.onIconUpdated?.call(key, '');
                      Navigator.pop(ctx);
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text('Reset $label to default vector icon')),
                      );
                    },
                    child: const Text('Reset Default', style: TextStyle(color: Colors.red)),
                  ),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF1B5E20),
                    foregroundColor: Colors.white,
                  ),
                  onPressed: () {
                    final newUrl = controller.text.trim();
                    setState(() {
                      if (newUrl.isEmpty) {
                        _icons.remove(key);
                      } else {
                        _icons[key] = newUrl;
                      }
                    });
                    widget.onIconUpdated?.call(key, newUrl);
                    Navigator.pop(ctx);
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Updated icon for $label!')),
                    );
                  },
                  child: const Text('Apply to Mobile Screen'),
                ),
              ],
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Mobile Screen Icons'),
        backgroundColor: const Color(0xFF1B5E20),
        foregroundColor: Colors.white,
      ),
      body: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            color: const Color(0xFF1B5E20).withOpacity(0.06),
            child: Row(
              children: [
                const Icon(Icons.info_outline, color: Color(0xFF1B5E20)),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Super Admin: You can customize images and icons displayed across mobile screens (cards, app bar, quick actions).',
                    style: TextStyle(color: Colors.grey[800], fontSize: 13),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: ListView.builder(
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
                      horizontal: 16,
                      vertical: 10,
                    ),
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
                                  customImg,
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
                            style: const TextStyle(fontWeight: FontWeight.bold),
                          ),
                        ),
                        if (hasCustom)
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xFFFFD700).withOpacity(0.2),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: const Text(
                              'CUSTOM IMAGE',
                              style: TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.bold,
                                color: Color(0xFFB8860B),
                              ),
                            ),
                          ),
                      ],
                    ),
                    subtitle: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: 4),
                        Text(
                          'Screen: ${slot['screen']}',
                          style: TextStyle(color: Colors.grey[700], fontSize: 12),
                        ),
                        Text(
                          hasCustom ? customImg : 'Using default system icon',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: hasCustom ? Colors.blue[700] : Colors.grey[500],
                            fontSize: 11,
                          ),
                        ),
                      ],
                    ),
                    trailing: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF1B5E20),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      ),
                      icon: const Icon(Icons.add_photo_alternate, size: 16),
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
