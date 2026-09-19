import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

/// Typed error for anything that goes wrong talking to the Ahsan Traders API.
class ApiException implements Exception {
  ApiException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  @override
  String toString() => message;
}

/// Thin HTTP client for the FastAPI backend (Ahsan Traders).
///
/// Uses [HttpClient] from dart:io so no extra package is required. All money
/// and authorization rules stay on the server; this class only transports
/// requests and surfaces readable errors.
class ApiService {
  ApiService({String? baseUrl, String? token})
      : baseUrl = baseUrl ?? '',
        token = token ?? '';

  String baseUrl;
  String token;

  /// Returns `http://host:port` with no trailing slash, adding the scheme when
  /// the user only typed `10.0.2.2:8000`.
  String get normalizedBase {
    var u = baseUrl.trim();
    if (u.isEmpty) return u;
    if (!u.startsWith('http://') && !u.startsWith('https://')) {
      u = 'http://$u';
    }
    while (u.endsWith('/')) {
      u = u.substring(0, u.length - 1);
    }
    return u;
  }

  Uri _uri(String path, [Map<String, String>? query]) {
    final suffix = (query == null || query.isEmpty)
        ? ''
        : '?${Uri(queryParameters: query).query}';
    return Uri.parse('$normalizedBase/api/v1$path$suffix');
  }

  Future<dynamic> _request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
    String? idempotencyKey,
  }) async {
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 10);
    try {
      final request = await client.openUrl(method, _uri(path, query));
      request.headers.set('Accept', 'application/json');
      if (token.trim().isNotEmpty) {
        request.headers.set('Authorization', 'Bearer $token');
      }
      if (idempotencyKey != null && idempotencyKey.isNotEmpty) {
        request.headers.set('Idempotency-Key', idempotencyKey);
      }
      if (body != null) {
        request.headers.set('Content-Type', 'application/json');
        request.write(jsonEncode(body));
      }
      final response = await request.close();
      final text = await response.transform(utf8.decoder).join();
      final decoded = text.isEmpty ? null : jsonDecode(text);
      if (response.statusCode >= 200 && response.statusCode < 300) {
        return decoded;
      }
      throw ApiException(
        _detail(decoded) ?? 'Request failed (HTTP ${response.statusCode})',
        statusCode: response.statusCode,
      );
    } on SocketException {
      throw ApiException(
        'Cannot reach the server at $normalizedBase.\n'
        'Check the address and make sure the backend is running.',
      );
    } on HttpException catch (e) {
      throw ApiException('Network error: ${e.message}');
    } on FormatException {
      throw ApiException('Received an invalid response from the server.');
    } finally {
      client.close(force: true);
    }
  }

  String? _detail(dynamic decoded) {
    if (decoded is Map) {
      final d = decoded['detail'];
      if (d is String) return d;
      if (d is List && d.isNotEmpty) {
        final first = d.first;
        if (first is Map && first['msg'] != null) {
          return first['msg'].toString();
        }
        return d.toString();
      }
    }
    return null;
  }

  Map<String, dynamic> _map(dynamic value) =>
      Map<String, dynamic>.from(value as Map);

  // ---------------------------------------------------------------- auth

  /// Logs in with a phone + password. Returns the token payload.
  Future<Map<String, dynamic>> login(String phone, String password) async {
    return _map(await _request('POST', '/auth/login', body: {
      'phone': phone,
      'password': password,
    }));
  }

  /// Returns the current account (id, name, phone, role, language, kyc).
  Future<Map<String, dynamic>> me() async {
    return _map(await _request('GET', '/me'));
  }

  /// Revokes all tokens for the current account on the server.
  Future<void> logout() async {
    await _request('POST', '/auth/logout');
  }

  // ---------------------------------------------------------- user admin

  /// Lists users. SUPERADMIN only. Pass [role] (ADMIN/INVESTOR/SUPERADMIN) or
  /// [search] to filter/serve-search, or null for everyone.
  Future<List<Map<String, dynamic>>> users({
    String? role,
    String? search,
  }) async {
    final query = <String, String>{};
    if (role != null && role.isNotEmpty) query['role'] = role;
    if (search != null && search.trim().isNotEmpty) query['search'] = search;
    final raw = await _request(
      'GET',
      '/admin/users',
      query: query.isEmpty ? null : query,
    );
    return (raw as List).map((e) => _map(e)).toList();
  }

  /// A single user by id. SUPERADMIN only.
  Future<Map<String, dynamic>> user(String userId) async {
    return _map(await _request('GET', '/admin/users/$userId'));
  }

  /// All businesses visible to the current account (SUPERADMIN sees all).
  Future<List<Map<String, dynamic>>> businesses() async {
    final raw = await _request('GET', '/admin/businesses');
    return (raw as List).map((e) => _map(e)).toList();
  }

  /// Creates a manager (role = ADMIN) and assigns it to the given businesses.
  Future<Map<String, dynamic>> createManager(
    String name,
    String phone,
    String password, {
    List<String> businessIds = const [],
  }) async {
    final created = _map(await _request('POST', '/admin/managers', body: {
      'name': name,
      'phone': phone,
      'password': password,
    }));
    final id = created['id'] as String;
    for (final businessId in businessIds) {
      await assignBusiness(id, businessId);
    }
    return created;
  }

  /// Registers an investor account (public endpoint).
  Future<Map<String, dynamic>> registerInvestor(
    String name,
    String phone,
    String password,
  ) async {
    return _map(await _request('POST', '/auth/register', body: {
      'name': name,
      'phone': phone,
      'password': password,
    }));
  }

  /// Changes a user's role. SUPERADMIN only.
  Future<Map<String, dynamic>> updateRole(String userId, String role) async {
    return _map(
      await _request('PUT', '/admin/users/$userId/role', body: {'role': role}),
    );
  }

  /// Marks a user KYC VERIFIED. SUPERADMIN only.
  Future<Map<String, dynamic>> verifyKyc(String userId) async {
    return _map(
      await _request('POST', '/admin/users/$userId/verify-kyc'),
    );
  }

  Future<void> assignBusiness(String userId, String businessId) async {
    await _request(
      'PUT',
      '/admin/businesses/$businessId/managers/$userId',
    );
  }

  Future<void> unassignBusiness(String userId, String businessId) async {
    await _request(
      'DELETE',
      '/admin/businesses/$businessId/managers/$userId',
    );
  }

  /// Reconciles the user's business assignments to exactly match [selected].
  Future<void> setUserBusinesses(
    String userId,
    List<String> current,
    List<String> selected,
  ) async {
    for (final id in current) {
      if (!selected.contains(id)) await unassignBusiness(userId, id);
    }
    for (final id in selected) {
      if (!current.contains(id)) await assignBusiness(userId, id);
    }
  }

  // -------------------------------------------------------------- icons

  /// Configured screen icons. SUPERADMIN only.
  Future<List<Map<String, dynamic>>> icons() async {
    final raw = await _request('GET', '/admin/icons');
    return (raw as List).map((e) => _map(e)).toList();
  }

  /// Saves the image for one screen/action icon. SUPERADMIN only.
  Future<Map<String, dynamic>> setIcon(
    String key, {
    required String label,
    required String screen,
    required String imageUrl,
    String? fallbackIcon,
  }) async {
    return _map(
      await _request('PUT', '/admin/icons/$key', body: {
        'label': label,
        'screen': screen,
        'image_url': imageUrl,
        if (fallbackIcon != null) 'fallback_icon': fallbackIcon,
      }),
    );
  }

  /// Uploads a base64-encoded image and returns its server path
  /// (`/uploads/icons/...`), ready to feed into [setIcon].
  Future<Map<String, dynamic>> uploadIconBase64(
    String filename,
    String data,
  ) async {
    return _map(
      await _request('POST', '/admin/icons/upload-base64', body: {
        'filename': filename,
        'data': data,
      }),
    );
  }

  /// Uploads raw image bytes and returns its server path.
  Future<Map<String, dynamic>> uploadIconFile(
    String filename,
    List<int> bytes,
    String contentType,
  ) {
    return _uploadMultipart('/admin/icons/upload', filename, bytes, contentType);
  }

  Future<Map<String, dynamic>> _uploadMultipart(
    String path,
    String filename,
    List<int> bytes,
    String contentType,
  ) async {
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 15);
    try {
      final boundary = 'flutter-${DateTime.now().microsecondsSinceEpoch}';
      final request = await client.postUrl(_uri(path));
      request.headers.set('Accept', 'application/json');
      if (token.trim().isNotEmpty) {
        request.headers.set('Authorization', 'Bearer $token');
      }
      request.headers.contentType =
          ContentType('multipart', 'form-data', parameters: {
        'boundary': boundary,
      });
      final buffer = BytesBuilder();
      buffer.add(utf8.encode(
          '--$boundary\r\nContent-Disposition: form-data; name="file"; filename="$filename"\r\n'));
      buffer.add(utf8.encode('Content-Type: $contentType\r\n\r\n'));
      buffer.add(bytes);
      buffer.add(utf8.encode('\r\n--$boundary--\r\n'));
      request.contentLength = buffer.length;
      request.add(buffer.takeBytes());
      final response = await request.close();
      final text = await response.transform(utf8.decoder).join();
      final decoded = text.isEmpty ? null : jsonDecode(text);
      if (response.statusCode >= 200 && response.statusCode < 300) {
        return _map(decoded);
      }
      throw ApiException(
        _detail(decoded) ?? 'Upload failed (HTTP ${response.statusCode})',
        statusCode: response.statusCode,
      );
    } on SocketException {
      throw ApiException(
        'Cannot reach the server at $normalizedBase to upload the image.',
      );
    } on HttpException catch (e) {
      throw ApiException('Network error: ${e.message}');
    } finally {
      client.close(force: true);
    }
  }
}
