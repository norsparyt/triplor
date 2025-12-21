import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

enum NavTab {
  home('/home', Icons.home_outlined, Icons.home, 'Home'),
  requests('/requests', Icons.handshake_outlined, Icons.handshake, 'Requests'),
  chats('/chats', Icons.chat_outlined, Icons.chat, 'Chats'),
  profile('/profile', Icons.person_outline, Icons.person, 'Profile');

  final String path;
  final IconData icon;
  final IconData selectedIcon;
  final String label;

  const NavTab(this.path, this.icon, this.selectedIcon, this.label);
}

class BottomNavScaffold extends StatelessWidget {
  final Widget child;

  const BottomNavScaffold({super.key, required this.child});

  int _getCurrentIndex(String location) {
    return NavTab.values
        .indexWhere((tab) => location.startsWith(tab.path))
        .clamp(0, NavTab.values.length - 1);
  }//grab the uri and check if starts with /home or /chat ie tab path

  @override
  Widget build(BuildContext context) {
    final location = GoRouterState.of(context).uri.toString();
    final currentIndex = _getCurrentIndex(location);

    return Scaffold(
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: currentIndex,
        onDestinationSelected: (index) {
          final tab = NavTab.values[index];
          context.go(tab.path);
        },
        //TODO remove hardcoding here
        destinations: NavTab.values.map((tab) {
          return NavigationDestination(
            icon: Icon(tab.icon),
            selectedIcon: Icon(tab.selectedIcon),
            label: tab.label,
          );
        }).toList(),
      ),
    );
  }
}
