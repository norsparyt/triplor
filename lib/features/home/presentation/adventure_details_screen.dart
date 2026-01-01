// import 'package:flutter/material.dart';
// import 'package:flutter_riverpod/flutter_riverpod.dart';
// import 'package:go_router/go_router.dart';
// import 'package:triplor/features/home/providers/adventure_providers.dart';
//
// import '../../../app/theme/app_colors.dart';
// import '../../../core/constants/app_strings.dart';
//
// class AdventureDetailsScreen extends ConsumerWidget {
//   final String adventureId;
//
//   AdventureDetailsScreen({super.key, required this.adventureId});
//   @override
//   Widget build(BuildContext context, WidgetRef ref) {
//     final adventureAsync = ref.watch(adventureDetailProvider(this.adventureId));
//     return Scaffold(
//       appBar: AppBar(
//         backgroundColor: AppColors.globalWhite,
//         elevation: 0,
//         leading: TextButton(
//           onPressed: () => context.go(AppStrings.homeRoute),
//           child: Text(
//             AppStrings.cancelCreateAdventure,
//             style: TextStyle(color: AppColors.grey700, fontSize: 16),
//           ),
//         ),
//         leadingWidth: 80,
//         title: Text('Adventure Details'),
//       ),
//       body: adventureAsync.when(
//         data: (adventure) => Padding(
//           padding: const EdgeInsets.all(16),
//           child: Column(
//             crossAxisAlignment: CrossAxisAlignment.start,
//             children: [
//               Text(
//                 adventure.location.displayName,
//                 style: Theme.of(context).textTheme.headlineSmall,
//               ),
//
//               const SizedBox(height: 8),
//
//               Text(
//                 '${adventure.dateRange.startDate} → ${adventure.dateRange.endDate}',
//               ),
//
//               const SizedBox(height: 8),
//
//               Text('Type: ${adventure.styles.first}'),
//
//               const SizedBox(height: 12),
//
//               Text(adventure.description),
//             ],
//           ),
//         ),
//         loading: () => const Center(child: CircularProgressIndicator()),
//         error: (err, stack) => Center(child: Text('Error: $err')),
//       ),
//     );
//   }
// }

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/constants/app_strings.dart';
import '../domain/models/adventure_model.dart';
import '../providers/adventure_providers.dart';

class AdventureDetailsScreen extends ConsumerStatefulWidget {
  final String adventureId;

  const AdventureDetailsScreen({super.key, required this.adventureId});

  @override
  ConsumerState<AdventureDetailsScreen> createState() =>
      _AdventureDetailsScreenState();
}

class _AdventureDetailsScreenState
    extends ConsumerState<AdventureDetailsScreen> {
  bool isFavorite = false;
  bool showFullDescription = false;

  @override
  Widget build(BuildContext context) {
    final adventureAsync = ref.watch(
      adventureDetailProvider(widget.adventureId),
    );

    return adventureAsync.when(
      data: (adventure) => Scaffold(
        body: Stack(
          children: [
            // Main scrollable content
            CustomScrollView(
              slivers: [
                // Hero image with gradient overlay
                _buildHeroImage(adventure),
                // White content section
                SliverToBoxAdapter(
                  child: Container(
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.only(
                        topLeft: Radius.circular(24),
                        topRight: Radius.circular(24),
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(height: 24),

                        // Info cards section
                        _buildInfoCards(adventure),
                        SizedBox(height: 32),

                        // Adventure Styles section
                        _buildAdventureStyles(adventure),

                        SizedBox(height: 32),

                        // About the trip section
                        _buildAboutSection(adventure),

                        SizedBox(height: 32),

                        // Organizer section
                        _buildOrganizerSection(adventure),

                        SizedBox(height: 100), // Space for bottom button
                      ],
                    ),
                  ),
                ),
              ],
            ),

            // Top app bar buttons
            _buildTopBar(context),
          ],
        ),
      ),
      loading: () => Scaffold(
        body: Center(
          child: CircularProgressIndicator(color: Color(0xFF2196F3)),
        ),
      ),
      error: (err, stack) => Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline, size: 60, color: Colors.red),
              SizedBox(height: 16),
              Text('Error loading adventure'),
              SizedBox(height: 8),
              TextButton(
                onPressed: () => context.go(AppStrings.homeRoute),
                child: Text('Go Back'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeroImage(Adventure adventure) {
    return SliverAppBar(
      expandedHeight: 300,
      pinned: false,
      automaticallyImplyLeading: false,
      flexibleSpace: FlexibleSpaceBar(
        background: Stack(
          fit: StackFit.expand,
          children: [
            // Hero image (replace with actual image from adventure.imageUrl if you have it)
            Image.network(
              'https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=800',
              fit: BoxFit.cover,
              errorBuilder: (context, error, stackTrace) {
                return Container(
                  color: Colors.grey[300],
                  child: Icon(Icons.image, size: 80, color: Colors.grey[400]),
                );
              },
            ),
            // Status badge and title overlay
            Positioned(
              left: 20,
              bottom: 40,
              right: 20,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // "Open for Joining" badge
                  Container(
                    padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    decoration: BoxDecoration(
                      color: Color(0xFF00C853),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      'Open for Joining',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  SizedBox(height: 12),

                  // Adventure title
                  Text(
                    adventure.location.displayName ??
                        '${adventure.location.city} Adventure',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 32,
                      fontWeight: FontWeight.w700,
                      shadows: [
                        Shadow(
                          offset: Offset(0, 2),
                          blurRadius: 8,
                          color: Colors.black.withOpacity(0.3),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTopBar(BuildContext context) {
    return Positioned(
      top: 0,
      left: 0,
      right: 0,
      child: SafeArea(
        child: Padding(
          padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // Back button
              Container(
                decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.9),
                  shape: BoxShape.circle,
                ),
                child: IconButton(
                  icon: Icon(Icons.arrow_back, color: Colors.black),
                  onPressed: () => context.go(AppStrings.homeRoute),
                ),
              ),

              Row(
                children: [
                  // Favorite button
                  Container(
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.9),
                      shape: BoxShape.circle,
                    ),
                    child: IconButton(
                      icon: Icon(
                        isFavorite ? Icons.favorite : Icons.favorite_border,
                        color: isFavorite ? Colors.red : Colors.black,
                      ),
                      onPressed: () {
                        setState(() {
                          isFavorite = !isFavorite;
                        });
                      },
                    ),
                  ),
                  SizedBox(width: 8),

                  // Share button
                  Container(
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.9),
                      shape: BoxShape.circle,
                    ),
                    child: IconButton(
                      icon: Icon(Icons.share, color: Colors.black),
                      onPressed: () {
                        // TODO: Implement share functionality
                      },
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildInfoCards(Adventure adventure) {
    final duration =
        adventure.dateRange.endDate
            .difference(adventure.dateRange.startDate)
            .inDays +
        1;

    return Padding(
      padding: EdgeInsets.symmetric(horizontal: 20),
      child: Container(
        padding: EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.25),
              blurRadius: 10,
              offset: Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          children: [
            // Destination row
            _buildInfoRow(
              icon: Icons.location_on,
              iconColor: Color(0xFF2196F3),
              label: 'DESTINATION',
              value:
                  '${adventure.location.city}, ${adventure.location.country}',
            ),
            Divider(height: 32, color: Colors.grey[200]),

            // Dates and Duration row
            Row(
              children: [
                Expanded(
                  child: _buildInfoRow(
                    icon: Icons.calendar_today,
                    iconColor: Color(0xFFFF9800),
                    label: 'DATES',
                    value: _formatDateRange(adventure),
                  ),
                ),
                Container(width: 1, height: 50, color: Colors.grey[200]),
                SizedBox(width: 16),
                Expanded(
                  child: _buildInfoRow(
                    icon: Icons.schedule,
                    iconColor: Color(0xFF9C27B0),
                    label: 'DURATION',
                    value: '$duration Days',
                  ),
                ),
              ],
            ),
            //
            Divider(height: 32, color: Colors.grey[200]),
            //
            // // Group size row
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: _buildInfoRow(
                    icon: Icons.people,
                    iconColor: Color(0xFF9C27B0),
                    label: 'GROUP SIZE',
                    value: 'Max ${adventure.maxPeople} People',
                  ),
                ),

                // Avatar stack (placeholder)
                Row(
                  children: [
                    _buildAvatar('https://i.pravatar.cc/150?img=1'),
                    _buildAvatar('https://i.pravatar.cc/150?img=2'),
                    _buildAvatar('https://i.pravatar.cc/150?img=3'),
                    Container(
                      width: 36,
                      height: 36,
                      decoration: BoxDecoration(
                        color: Colors.grey[200],
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 2),
                      ),
                      child: Center(
                        child: Text(
                          '+3',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: Colors.grey[700],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAvatar(String imageUrl) {
    return Container(
      width: 36,
      height: 36,
      margin: EdgeInsets.only(left: 4),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white, width: 2),
        image: DecorationImage(
          image: NetworkImage(imageUrl),
          fit: BoxFit.cover,
        ),
      ),
    );
  }

  Widget _buildInfoRow({
    required IconData icon,
    required Color iconColor,
    required String label,
    required String value,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          padding: EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: iconColor.withOpacity(0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(icon, color: iconColor, size: 20),
        ),
        SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: Colors.grey[600],
                  letterSpacing: 0.5,
                ),
              ),
              SizedBox(height: 4),
              Text(
                value,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: Colors.black,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildAdventureStyles(Adventure adventure) {
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Adventure Styles',
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w700,
              color: Colors.black,
            ),
          ),
          SizedBox(height: 16),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: adventure.styles.map((style) {
              return _buildStyleChip(style.label);
            }).toList(),
          ),
        ],
      ),
    );
  }

  Widget _buildStyleChip(String style) {
    final styleData = _getStyleData(style);

    return Container(
      padding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: styleData['color'].withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(styleData['icon'], color: styleData['color'], size: 20),
          SizedBox(width: 8),
          Text(
            style,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w600,
              color: styleData['color'],
            ),
          ),
        ],
      ),
    );
  }

  Map<String, dynamic> _getStyleData(String style) {
    switch (style.toLowerCase()) {
      case 'nature':
        return {'icon': Icons.nature, 'color': Color(0xFF4CAF50)};
      case 'photography':
        return {'icon': Icons.camera_alt, 'color': Color(0xFFE91E63)};
      case 'foodie':
        return {'icon': Icons.restaurant, 'color': Color(0xFF2196F3)};
      case 'culture':
        return {'icon': Icons.account_balance, 'color': Color(0xFF00BCD4)};
      case 'backpacking':
        return {'icon': Icons.backpack, 'color': Color(0xFFFF9800)};
      case 'hiking':
        return {'icon': Icons.hiking, 'color': Color(0xFF795548)};
      default:
        return {'icon': Icons.explore, 'color': Colors.grey};
    }
  }

  Widget _buildAboutSection(Adventure adventure) {
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'About the trip',
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w700,
              color: Colors.black,
            ),
          ),
          SizedBox(height: 12),
          Text(
            adventure.description,
            style: TextStyle(
              fontSize: 15,
              color: Colors.grey[700],
              height: 1.6,
            ),
            maxLines: showFullDescription ? null : 6,
            overflow: showFullDescription ? null : TextOverflow.ellipsis,
          ),
          if (adventure.description.length > 200)
            TextButton(
              onPressed: () {
                setState(() {
                  showFullDescription = !showFullDescription;
                });
              },
              style: TextButton.styleFrom(
                padding: EdgeInsets.zero,
                minimumSize: Size(0, 0),
              ),
              child: Text(
                showFullDescription ? 'Read less' : 'Read more',
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF2196F3),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildOrganizerSection(Adventure adventure) {
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Organizer',
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w700,
              color: Colors.black,
            ),
          ),
          SizedBox(height: 16),
          Container(
            padding: EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.grey[200]!),
            ),
            child: Row(
              children: [
                // Avatar
                Stack(
                  children: [
                    CircleAvatar(
                      radius: 30,
                      backgroundImage: NetworkImage(
                        'https://i.pravatar.cc/150?img=5',
                      ),
                    ),
                    Positioned(
                      right: 0,
                      bottom: 0,
                      child: Container(
                        padding: EdgeInsets.all(2),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          Icons.verified,
                          color: Color(0xFF2196F3),
                          size: 18,
                        ),
                      ),
                    ),
                  ],
                ),
                SizedBox(width: 12),

                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Sarah Jenkins', // Replace with actual organizer name
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: Colors.black,
                        ),
                      ),
                      SizedBox(height: 4),
                      Row(
                        children: [
                          Icon(Icons.star, color: Color(0xFFFFB300), size: 16),
                          SizedBox(width: 4),
                          Text(
                            '4.9',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: Colors.grey[700],
                            ),
                          ),
                          Text(
                            ' (24 trips)',
                            style: TextStyle(
                              fontSize: 14,
                              color: Colors.grey[500],
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),

                // Profile button
                TextButton(
                  onPressed: () {
                    // TODO: Navigate to organizer profile
                  },
                  style: TextButton.styleFrom(
                    backgroundColor: Color(0xFFE3F2FD),
                    padding: EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: Text(
                    'Profile',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF2196F3),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _showJoinDialog(Adventure adventure) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text('Join Adventure?'),
        content: Text(
          'Are you sure you want to join "${adventure.location.displayName ?? adventure.location.city} Adventure"?',
        ),
        actions: [
          TextButton(
            onPressed: () => context.go(AppStrings.homeRoute),
            child: Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              context.go(AppStrings.homeRoute);
              // TODO: Implement join logic
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('Successfully joined the adventure!'),
                  backgroundColor: Colors.green,
                ),
              );
            },
            style: ElevatedButton.styleFrom(backgroundColor: Color(0xFF2196F3)),
            child: Text('Confirm'),
          ),
        ],
      ),
    );
  }

  String _formatDateRange(Adventure adventure) {
    final formatter = DateFormat('MMM d');
    final start = formatter.format(adventure.dateRange.startDate);
    final end = DateFormat('MMM d').format(adventure.dateRange.endDate);
    return '$start - $end';
  }
}
