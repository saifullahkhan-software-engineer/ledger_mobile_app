class ScreenIconItem {
  final String key;
  final String label;
  final String screen;
  final String imageUrl;
  final String? fallbackIcon;

  const ScreenIconItem({
    required this.key,
    required this.label,
    required this.screen,
    required this.imageUrl,
    this.fallbackIcon,
  });

  factory ScreenIconItem.fromJson(Map<String, dynamic> json) {
    return ScreenIconItem(
      key: json['key'] as String,
      label: json['label'] as String,
      screen: json['screen'] as String? ?? 'dashboard',
      imageUrl: json['image_url'] as String,
      fallbackIcon: json['fallback_icon'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'key': key,
        'label': label,
        'screen': screen,
        'image_url': imageUrl,
        'fallback_icon': fallbackIcon,
      };
}
