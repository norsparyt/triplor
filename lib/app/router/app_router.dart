import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../features/chat/presentation/chats_screen.dart';
import '../../features/home/presentation/home_screen.dart';
import '../../features/profile/presentation/profile_screen.dart';
import '../../features/requests/presentation/requests_screen.dart';
import '../widgets/bottom_nav_scaffold.dart';

final goRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/home',
    routes: [
      ShellRoute(
        builder: (context, state, child) {
          return BottomNavScaffold(child: child);
        },
        routes: [
          GoRoute(
            path: '/home',
            pageBuilder: (context, state) =>
                const NoTransitionPage(child: HomeScreen()),
          ),
          GoRoute(
            path: '/requests',
            pageBuilder: (context, state) =>
                const NoTransitionPage(child: RequestsScreen()),
          ),
          GoRoute(
            path: '/chats',
            pageBuilder: (context, state) =>
            const NoTransitionPage(child: ChatsScreen()),
          ),
          GoRoute(
            path: '/profile',
            pageBuilder: (context, state) =>
                const NoTransitionPage(child: ProfileScreen()),
          ),
        ],
      ),
    ],
  );
});
