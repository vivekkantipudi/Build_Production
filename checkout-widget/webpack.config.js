const path = require('path');

module.exports = {
  entry: './src/sdk/PaymentGateway.js', // Entry point for the SDK
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: 'checkout.js', // The bundled output
    library: 'PaymentGateway', // Expose as window.PaymentGateway
    libraryTarget: 'umd',
    libraryExport: 'default', // Export the default class directly
    globalObject: 'this'
  },
  module: {
    rules: [
      {
        test: /\.(js|jsx)$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: ['@babel/preset-env', '@babel/preset-react']
          }
        }
      },
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader'] // Bundles CSS into the JS file
      }
    ]
  },
  resolve: {
    extensions: ['.js', '.jsx']
  }
};