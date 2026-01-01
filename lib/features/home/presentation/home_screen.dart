import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:triplor/features/home/providers/adventure_providers.dart';

import '../../../app/theme/app_colors.dart';
import '../../../core/constants/app_strings.dart';
import '../domain/models/adventure_model.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});
  //TODO redo UI
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final adventuresAsync = ref.watch(allAdventuresProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('My Adventures')),
      body: adventuresAsync.when(
        data: (adventure) {
          return ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: adventure.length,
            itemBuilder: (BuildContext context, int index) {
              return Column(
                children: [
                  SizedBox(height: 16),
                  AdventureCard(
                    id: adventure[index].id,
                    title: adventure[index].location.displayName,
                    date: _formatDateRange(adventure[index]),
                    type: adventure[index].styles.first.label,
                    travelMode: adventure[index].maxPeople == 1
                        ? 'Solo'
                        : 'Group Trip',
                    status: 'PLANNING',
                    //todo feature implementation
                    badgeText: '5 Interested',
                    //todo feature implementation
                    gradient: [AppColors.primaryColorGlobal, Color(0xFF74C69D)],
                  ),
                ],
              );
            },
          );
        },
        error: (error, stack) {
          return Center(child: Text('Error: $error'));
        },
        loading: () => Center(child: CircularProgressIndicator()),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () {
          context.go('/createAdventure');
        },
        backgroundColor: Color(0xFF2196F3),
        icon: Icon(Icons.add, color: Colors.white),
        label: Text(
          'New Adventure',
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
        ),
        elevation: 4,
      ),
    );
  }

  String _formatDateRange(Adventure adventure) {
    final formatter = DateFormat('MMM d, yyyy');
    final start = formatter.format(adventure.dateRange.startDate);
    final end = formatter.format(adventure.dateRange.endDate);

    // If same month and year, show shorter format
    if (adventure.dateRange.startDate.month ==
            adventure.dateRange.endDate.month &&
        adventure.dateRange.startDate.year ==
            adventure.dateRange.endDate.year) {
      final monthFormatter = DateFormat('MMM d');
      return '${monthFormatter.format(adventure.dateRange.startDate)} - ${adventure.dateRange.endDate.day}, ${adventure.dateRange.endDate.year}';
    }

    return '$start - $end';
  }
}

class AdventureCard extends StatelessWidget {
  final String id;
  final String title;
  final String date;
  final String type;
  final String travelMode;
  final String status;
  final String badgeText;
  final List<Color> gradient;

  const AdventureCard({
    super.key,
    required this.id,
    required this.title,
    required this.date,
    required this.type,
    required this.travelMode,
    required this.status,
    required this.badgeText,
    required this.gradient,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => context.goNamed(
        AppStrings.adventureDetails,
        pathParameters: {'id': id},
      ),
      child: Container(
        height: 260,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(24),
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: gradient,
          ),
        ),
        child: Stack(
          children: [
            // Top badges
            Positioned(top: 16, left: 16, child: _Pill(text: badgeText)),
            Positioned(top: 16, right: 16, child: _Pill(text: status)),

            // Bottom content
            Positioned(
              left: 16,
              right: 16,
              bottom: 16,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      _InfoChip(icon: Icons.calendar_today, label: date),
                      _InfoChip(icon: Icons.person, label: travelMode),
                      _InfoChip(icon: Icons.explore, label: type),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Pill extends StatelessWidget {
  final String text;

  const _Pill({required this.text});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.35),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        text,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 12,
          fontWeight: FontWeight.w500,
        ),
      ),
    );
  }
}

class _InfoChip extends StatelessWidget {
  final IconData icon;
  final String label;

  const _InfoChip({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.35),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Icon(icon, size: 14, color: Colors.white),
          const SizedBox(width: 4),
          Text(
            label,
            style: const TextStyle(color: Colors.white, fontSize: 12),
          ),
        ],
      ),
    );
  }
}
