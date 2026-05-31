const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

const PORT = process.env.PORT || 3000;

// Health check endpoint
app.get('/', (req, res) => {
  res.status(200).send('Signaling server is healthy and running.');
});

io.on('connection', (socket) => {
  console.log(`Client connected: ${socket.id}`);

  // Join a room based on room Name or familyCode
  socket.on('join', (data) => {
    console.log(`Join event from client ${socket.id} with payload:`, data);
    let room = '';
    if (typeof data === 'string') {
      room = data;
    } else if (data && typeof data === 'object') {
      room = data.room || `room-${data.familyCode}` || data.familyCode;
    }
    
    if (room) {
      socket.join(room);
      console.log(`Socket ${socket.id} joined room: ${room}`);
      // Notify other clients in the room
      socket.to(room).emit('user-joined', { socketId: socket.id, role: data?.role });
    }
  });

  // Relay generic messages (offer, answer, candidate, etc. sent via 'message' event)
  socket.on('message', (data) => {
    console.log(`Relaying message from ${socket.id} to room:`, data);
    let room = '';
    if (data && typeof data === 'object') {
      room = data.room || `room-${data.familyCode}` || data.familyCode;
    }
    if (room) {
      socket.to(room).emit('message', data);
    } else {
      // Fallback: broadcast to all other sockets if no room defined
      socket.broadcast.emit('message', data);
    }
  });

  // Handle explicit signaling events
  const relayEvents = ['offer', 'answer', 'candidate', 'leave'];
  relayEvents.forEach((event) => {
    socket.on(event, (data) => {
      console.log(`Relaying explicit event '${event}' from ${socket.id}:`, data);
      let room = '';
      if (data && typeof data === 'object') {
        room = data.room || `room-${data.familyCode}` || data.familyCode;
      }
      if (room) {
        socket.to(room).emit(event, data);
      } else {
        socket.broadcast.emit(event, data);
      }
    });
  });

  socket.on('disconnect', (reason) => {
    console.log(`Client disconnected: ${socket.id} (${reason})`);
  });
});

server.listen(PORT, () => {
  console.log(`Signaling server listening on port ${PORT}`);
});
