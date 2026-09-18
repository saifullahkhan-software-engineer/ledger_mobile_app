class AppUser {
  final String id;
  final String phone;
  final String name;
  final String role;
  final String language;
  final String kycStatus;
  final List<String> assignedBusinesses;

  const AppUser({
    required this.id,
    required this.phone,
    required this.name,
    required this.role,
    this.language = 'en',
    this.kycStatus = 'UNVERIFIED',
    this.assignedBusinesses = const [],
  });

  factory AppUser.fromJson(Map<String, dynamic> json) {
    return AppUser(
      id: json['id'] as String,
      phone: json['phone'] as String,
      name: json['name'] as String,
      role: json['role'] as String,
      language: json['language'] as String? ?? 'en',
      kycStatus: json['kyc_status'] as String? ?? 'UNVERIFIED',
      assignedBusinesses: (json['assigned_businesses'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'phone': phone,
        'name': name,
        'role': role,
        'language': language,
        'kyc_status': kycStatus,
        'assigned_businesses': assignedBusinesses,
      };
}
